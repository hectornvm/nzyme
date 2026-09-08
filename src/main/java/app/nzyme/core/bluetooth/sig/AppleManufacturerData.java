package app.nzyme.core.bluetooth.sig;

import java.util.Base64;
import java.util.Optional;

/**
 * Node-side decoder for Apple (company_id 76) manufacturer advertising payloads.
 *
 * The tap's apple_findmy tagger only classifies the FindMy type bytes 0x07
 * (unpaired) and 0x12 (paired). Many other Apple payload types (e.g. 0x09
 * rotating-key FindMy/Continuity broadcasts) are NOT tagged by the tap, so they
 * appear as generic "Apple" devices. This decoder runs node-side on the stored
 * base64 manufacturer_data to classify those, complementing the tap taggers.
 *
 * Only asserts what is reliably known; other Apple types are reported by their
 * raw payload type byte (research-gated for a richer table in a later pass).
 *
 * LOCAL ONLY (BT enrichment work - never push upstream).
 */
public class AppleManufacturerData {

    private static final int APPLE_COMPANY_ID = 76;

    // Apple manufacturer-payload type bytes (payload[0]; company-ID prefix already
    // stripped by BlueZ). Labels from public reverse engineering:
    //   0x05 AirDrop (MetaRadar APPLE_AIRDROP_PACKAGE_TYPE=0x05)
    //   0x07 Find My (tap tagger unpaired gate)
    //   0x0a / 0x0c Apple Continuity Nearby Action / Nearby Info
    //   0x12 Find My "offline finding" key (OpenHaystack Advertisement.swift subtype 0x12)
    // Types 0x01/0x09/0x10/0x13 etc. remain research-gated (reported by raw byte).
    private static final int TYPE_AIRDROP = 0x05;
    private static final int TYPE_FIND_MY_UNPAIRED = 0x07;
    private static final int TYPE_NEARBY_ACTION = 0x0a;
    private static final int TYPE_NEARBY_INFO = 0x0c;
    private static final int TYPE_FIND_MY_PAIRED = 0x12;

    public static Optional<AppleClassification> classify(Integer companyId, String manufacturerDataBase64) {
        if (companyId == null || companyId != APPLE_COMPANY_ID || manufacturerDataBase64 == null) {
            return Optional.empty();
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(manufacturerDataBase64);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (payload.length == 0) {
            return Optional.empty();
        }

        int type = payload[0] & 0xFF;
        String label;
        switch (type) {
            case TYPE_AIRDROP:
                label = "AirDrop (Continuity)";
                break;
            case TYPE_FIND_MY_UNPAIRED:
                label = "Find My (unpaired)";
                break;
            case TYPE_NEARBY_ACTION:
                label = "Nearby Action (Continuity)";
                break;
            case TYPE_NEARBY_INFO:
                label = "Nearby Info (Continuity)";
                break;
            case TYPE_FIND_MY_PAIRED:
                label = "Find My (paired)";
                break;
            default:
                // Reported by raw type byte; semantic label is research-gated.
                label = "Apple advertising type 0x" + String.format("%02x", type);
        }

        return Optional.of(new AppleClassification(type, label));
    }

    public record AppleClassification(int type, String label) {
        public String typeHex() {
            return "0x" + String.format("%02x", type);
        }
    }

}
