package app.nzyme.core.bluetooth.classification;

import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Passive classification of Bluetooth devices from data that is already advertised (Plan H
 * Pillar 2): Class of Device, advertised service UUIDs and the Appearance characteristic.
 *
 * Deliberately passive: no connections, no active GATT reads (that's the deferred Pillar 4).
 * The goal is a human-meaningful `device_type` tag for the device list, not identification of
 * a specific product (which would need manufacturer/service-data payload decode - Pillar 2b).
 *
 * Precedence:
 *  1. Class of Device (most specific when set; covers TVs, speakers, headphones, laptops, ...)
 *  2. Advertised service UUIDs (audio/HID/phone/health/fitness roles, known vendor services)
 *  3. Appearance (covers watches, remotes, HID devices that carry no CoD)
 *
 * Devices the tap already positively identified (trackers, FindMy, Meshtastic) are left alone.
 *
 * LOCAL ONLY (BT enrichment work - never push upstream).
 */
public class BluetoothDeviceClassifier {

    /**
     * @param type   machine-readable type key (stable, for filtering/UI)
     * @param label  human-readable description
     * @param source where the classification came from: cod | service_uuid | appearance
     */
    public record Classification(String type, String label, String source) {
    }

    private record Type(String type, String label) {
    }

    private static final Set<String> ALREADY_IDENTIFIED_TAGS = Set.of(
            "samsung_smarttag",
            "tile",
            "chipolo",
            "google_find_my_device",
            "apple_find_my_paired",
            "apple_find_my_unpaired",
            "meshtastic_node"
    );

    private static final Type TV = new Type("tv", "TV / display");
    private static final Type SPEAKER = new Type("speaker", "Speaker");
    private static final Type HEADPHONES = new Type("headphones", "Headphones");
    private static final Type AUDIO = new Type("audio", "Audio device");
    private static final Type INPUT = new Type("input_device", "Input device");
    private static final Type PHONE = new Type("phone", "Phone");
    private static final Type HEALTH = new Type("health", "Health device");
    private static final Type FITNESS = new Type("fitness", "Fitness device");
    private static final Type SMART_HOME = new Type("smart_home", "Smart home device");

    // Priority-ordered: first match wins. More specific / less ambiguous roles first.
    private static final Map<String, Type> SERVICE_UUID_TYPES;
    static {
        Map<String, Type> m = new LinkedHashMap<>();
        // HID is unambiguous.
        m.put("1812", new Type("input_device", "Input device (HID)"));
        // Headset profile.
        m.put("1108", HEADPHONES);
        // Headset/hands-free gateway roles are advertised by phones (audio devices advertise
        // sink/source roles instead), so these distinguish phones from audio gear.
        m.put("1112", PHONE);
        m.put("111f", PHONE);
        // Audio roles (A2DP source/sink, AVRCP target/controller).
        m.put("110a", AUDIO);
        m.put("110b", AUDIO);
        m.put("110c", AUDIO);
        m.put("110e", AUDIO);
        // Health.
        m.put("1808", HEALTH);
        m.put("1809", HEALTH);
        m.put("180d", HEALTH);
        m.put("1810", HEALTH);
        m.put("181b", HEALTH);
        m.put("181d", HEALTH);
        // Fitness.
        m.put("1814", FITNESS);
        m.put("1816", FITNESS);
        m.put("1818", FITNESS);
        m.put("1826", FITNESS);
        // Known vendor services (identified from live capture + SIG data).
        m.put("fe2c", new Type("fast_pair", "Google Fast Pair device"));
        m.put("fe07", new Type("audio", "Audio device (Sonos)"));
        m.put("febe", new Type("audio", "Audio device (Bose)"));
        m.put("fd50", new Type("smart_home", "Smart home device (Tuya)"));
        m.put("fe0f", new Type("smart_home", "Smart home device (Signify)"));
        m.put("fd56", new Type("medical", "Medical device (ResMed)"));
        SERVICE_UUID_TYPES = Collections.unmodifiableMap(m);
    }

    public static Optional<Classification> classify(@Nullable Integer classNumber,
                                                    @Nullable Integer appearance,
                                                    @Nullable List<String> serviceUuids,
                                                    @Nullable Map<String, ?> existingTags) {
        if (existingTags != null && !Collections.disjoint(existingTags.keySet(), ALREADY_IDENTIFIED_TAGS)) {
            // Already positively identified by a dedicated detector. Don't second-guess it.
            return Optional.empty();
        }

        // 1) Class of Device.
        if (classNumber != null && classNumber != 0) {
            Type type = fromClassOfDevice(classNumber);
            if (type != null) {
                return Optional.of(new Classification(type.type(), type.label(), "cod"));
            }
        }

        // 2) Advertised service UUIDs.
        if (serviceUuids != null && !serviceUuids.isEmpty()) {
            Type type = fromServiceUuids(serviceUuids);
            if (type != null) {
                return Optional.of(new Classification(type.type(), type.label(), "service_uuid"));
            }
        }

        // 3) Appearance.
        if (appearance != null && appearance != 0) {
            Type type = fromAppearance(appearance);
            if (type != null) {
                return Optional.of(new Classification(type.type(), type.label(), "appearance"));
            }
        }

        return Optional.empty();
    }

    private static Type fromClassOfDevice(int cod) {
        int major = (cod & 0x1F00) >> 8;
        int minor = (cod & 0xFC) >> 2;

        switch (major) {
            case 1: // Computer.
                switch (minor) {
                    case 1: return new Type("desktop", "Desktop computer");
                    case 2: return new Type("server", "Server");
                    case 3: return new Type("laptop", "Laptop");
                    case 6: return new Type("wearable", "Wearable computer");
                    default: return new Type("computer", "Computer");
                }
            case 2: // Phone.
                return PHONE;
            case 3: // LAN / Network Access Point.
                return new Type("network", "Network access point");
            case 4: // Audio/Video.
                switch (minor) {
                    case 1: return HEADPHONES; // Wearable headset device.
                    case 2: return new Type("headset", "Hands-free device");
                    case 4: return new Type("audio", "Microphone");
                    case 5: return SPEAKER; // Loudspeaker.
                    case 6: return HEADPHONES;
                    case 7: return new Type("portable_audio", "Portable audio");
                    case 8: return new Type("car_audio", "Car audio");
                    case 9: return new Type("tv", "Set-top box");
                    case 10: return new Type("speaker", "HiFi audio device");
                    case 11: return new Type("video", "Video recorder");
                    case 12:
                    case 13: return new Type("camera", "Video camera");
                    case 14: return TV; // Video monitor.
                    case 15: return new Type("tv", "TV / display with loudspeaker");
                    case 16: return new Type("conference", "Video conferencing device");
                    case 18: return new Type("toy", "Gaming / toy device");
                    default: return null; // 0 (uncategorized), 3 (reserved), 17.
                }
            case 5: { // Peripheral: bits 7-6 = keyboard/pointing, bits 5-2 = other devices.
                int keyboardPointing = (cod & 0xC0) >> 6;
                switch (keyboardPointing) {
                    case 1: return new Type("input_device", "Keyboard");
                    case 2: return new Type("input_device", "Pointing device");
                    case 3: return new Type("input_device", "Keyboard/pointing device");
                    default: break;
                }

                int other = (cod & 0x3C) >> 2;
                switch (other) {
                    case 1: return new Type("input_device", "Joystick");
                    case 2: return new Type("input_device", "Gamepad");
                    case 3: return new Type("input_device", "Remote control");
                    case 4: return new Type("sensor", "Sensing device");
                    case 5: return new Type("input_device", "Digitizer tablet");
                    case 6: return new Type("input_device", "Card reader");
                    case 7: return new Type("input_device", "Digital pen");
                    default: return null;
                }
            }
            case 6: // Imaging.
                switch (minor) {
                    case 1: return new Type("camera", "Camera");
                    case 2: return new Type("scanner", "Scanner");
                    case 3: return new Type("printer", "Printer");
                    default: return null;
                }
            case 8: // Wearable.
                return new Type("wearable", "Wearable");
            case 9: // Toy.
                return new Type("toy", "Toy");
            case 10: // Health.
                return HEALTH;
            default:
                return null;
        }
    }

    private static Type fromServiceUuids(List<String> serviceUuids) {
        Set<String> shortUuids = new HashSet<>();
        for (String uuid : serviceUuids) {
            String shortUuid = to16BitUuid(uuid);
            if (shortUuid != null) {
                shortUuids.add(shortUuid);
            }
        }

        for (Map.Entry<String, Type> entry : SERVICE_UUID_TYPES.entrySet()) {
            if (shortUuids.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    @Nullable
    private static String to16BitUuid(@Nullable String uuid) {
        if (uuid == null) {
            return null;
        }

        String u = uuid.toLowerCase().trim();
        // Standard Bluetooth base UUID form: 0000xxxx-0000-1000-8000-00805f9b34fb.
        if (u.length() == 36 && u.startsWith("0000") && u.endsWith("-0000-1000-8000-00805f9b34fb")) {
            return u.substring(4, 8);
        }
        // Already a bare 16-bit UUID.
        if (u.length() == 4) {
            return u;
        }
        return null;
    }

    private static Type fromAppearance(int appearance) {
        // Category is value & 0xFFC0, subcategory & 0x003F.
        int category = appearance & 0xFFC0;
        switch (category) {
            case 0x0040: return PHONE;
            case 0x0080: return new Type("computer", "Computer");
            case 0x00C0: return new Type("wearable", "Watch");
            case 0x0140: return new Type("tv", "Display");
            case 0x0180: return new Type("input_device", "Remote control");
            case 0x01C0: return new Type("wearable", "Eyeglasses");
            case 0x0200: return new Type("tracker", "Tag");
            case 0x0240: return new Type("tracker", "Keyring");
            case 0x0280: return new Type("media_player", "Media player");
            case 0x02C0: return new Type("scanner", "Barcode scanner");
            case 0x0300: return new Type("health", "Thermometer");
            case 0x0340: return HEALTH; // Heart rate sensor.
            case 0x0380: return new Type("health", "Blood pressure monitor");
            case 0x03C0: return INPUT; // Human interface device.
            case 0x0400: return new Type("health", "Glucose meter");
            case 0x0440: return new Type("fitness", "Running/walking sensor");
            case 0x0480: return new Type("fitness", "Cycling sensor");
            case 0x04C0: return new Type("control_device", "Control device");
            case 0x0500: return new Type("network", "Network device");
            case 0x0540: return new Type("sensor", "Sensor");
            case 0x0580: // Light fixture.
            case 0x05C0: // Fan.
            case 0x0600: // HVAC.
            case 0x0640: // Air conditioning.
            case 0x0680: // Humidifier.
            case 0x06C0: // Heating.
            case 0x0700: // Access control.
            case 0x0740: // Motorized device.
            case 0x0780: // Power device.
            case 0x07C0: // Light source.
            case 0x0800: return SMART_HOME; // Window covering.
            case 0x0840: // Audio sink.
            case 0x0880: return AUDIO; // Audio source.
            case 0x08C0: return new Type("vehicle", "Motorized vehicle");
            case 0x0900: return new Type("appliance", "Domestic appliance");
            case 0x0940: return HEADPHONES; // Wearable audio device.
            case 0x0980: return new Type("vehicle", "Aircraft");
            case 0x09C0: return new Type("audio", "AV equipment");
            case 0x0A00: return new Type("display", "Display equipment");
            case 0x0A40: return new Type("health", "Hearing aid");
            case 0x0A80: return new Type("toy", "Gaming device");
            default: return null;
        }
    }

}
