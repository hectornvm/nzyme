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
/// Detecting by the advertised service UUID is a strong, dedicated signal (these
/// UUIDs are reserved to the tracker offline-finding functions); the service-data
/// prefix would add marginal precision only.
///
/// Google Find My Device network trackers (Pebblebee/Chipolo/Motorola/Hama/Eufy/Jio)
/// broadcast Apple-compatible FindMy manufacturer payloads under non-Apple makers and
/// are NOT covered here (the Apple tagger's company==76 gate misses them) - defer.
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
        } else {
            continue;
        };

        return Some((tag.to_string(), parameters));
    }

    None
}
