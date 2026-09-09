use std::collections::HashMap;
use std::sync::Arc;
use crate::wireless::bluetooth::bluetooth_device_advertisement::BluetoothDeviceAdvertisement;
use crate::wireless::bluetooth::detection::device_tagger::TagValue;

/// Tags non-Apple find-my/offline-finding trackers by their DEDICATED service UUID
/// (from AirGuard, TU Darmstadt). Each ecosystem uses a reserved service UUID to
/// advertise its offline-finding presence:
///   Samsung SmartTag: 0xFD5A (service data prefix 0x10)
///   Tile:             0xFEED (service data prefix 0x02 0x00)
///   Chipolo:          0xFE33
///   Google Find My Device: 0xFEAA (service data subtype 0x40)
/// Detecting by the advertised service UUID is a strong, dedicated signal (these
/// UUIDs are reserved to the tracker offline-finding functions); the service-data
/// prefix/subtype would add marginal precision only. Google FMD maker distinction
/// (Pebblebee/Chipolo/Motorola/Hama/Eufy/Jio) needs deeper service-data parse - defer.
///
/// LOCAL ONLY (BT enrichment work - never push upstream).
pub fn tag(advertisement: &Arc<BluetoothDeviceAdvertisement>) 
    -> Option<(String, HashMap<String, TagValue>)> {
    let uuids = advertisement.uuids.as_ref()?;

    for uuid in uuids {
        let u = uuid.to_lowercase();
        let mut parameters: HashMap<String, TagValue> = HashMap::new();

        // Full 128-bit forms of the reserved 16-bit offline-finding service UUIDs.
        let tag = if u.contains("0000fd5a-0000-1000-8000-00805f9b34fb") {
            "samsung_smarttag"
        } else if u.contains("0000feed-0000-1000-8000-00805f9b34fb") {
            "tile"
        } else if u.contains("0000fe33-0000-1000-8000-00805f9b34fb") {
            "chipolo"
        } else if u.contains("0000feaa-0000-1000-8000-00805f9b34fb") {
            "google_find_my_device"
        } else {
            continue;
        };

        // Google FMD maker is identified from the ADVERTISED NAME (AirGuard: the
        // payload does not encode the maker; trackers brand their advertised name,
        // e.g. "Pebblebee Clip", "Moto Tag"). Match maker keywords to enrich the tag.
        if tag == "google_find_my_device" {
            if let Some(name) = advertisement.name.as_ref() {
                let lower = name.to_lowercase();
                let maker = if lower.contains("pebblebee") {
                    Some("pebblebee")
                } else if lower.contains("chipolo") {
                    Some("chipolo")
                } else if lower.contains("motorola") || lower.contains("moto tag") || lower.contains("tbd-gray") {
                    Some("motorola")
                } else if lower.contains("eufy") {
                    Some("eufy")
                } else if lower.contains("jio") {
                    Some("jio")
                } else if lower.contains("rolling square") {
                    Some("rolling_square")
                } else if lower.contains("hama") {
                    Some("hama")
                } else {
                    None
                };
                if let Some(m) = maker {
                    parameters.insert("maker".to_string(), TagValue::Text(m.to_string()));
                }
            }
        }

        return Some((tag.to_string(), parameters));
    }

    None
}
