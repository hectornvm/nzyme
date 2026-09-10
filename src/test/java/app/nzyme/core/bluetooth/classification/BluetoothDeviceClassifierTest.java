package app.nzyme.core.bluetooth.classification;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class BluetoothDeviceClassifierTest {

    private static final String A2DP_SINK = "0000110b-0000-1000-8000-00805f9b34fb";
    private static final String HID = "00001812-0000-1000-8000-00805f9b34fb";
    private static final String HFP_AG = "00001112-0000-1000-8000-00805f9b34fb";
    private static final String SONOS = "0000fe07-0000-1000-8000-00805f9b34fb";

    @Test
    public void testClassOfDevice() {
        // TV: major AV (4), minor 15 = Video Display and Loudspeaker. Observed on LG/Samsung TVs.
        BluetoothDeviceClassifier.Classification tv = BluetoothDeviceClassifier
                .classify(533564, null, null, null).orElseThrow();
        assertEquals(tv.type(), "tv");
        assertEquals(tv.source(), "cod");

        // Loudspeaker: major AV, minor 5.
        assertEquals(BluetoothDeviceClassifier.classify(1044, null, null, null).orElseThrow().type(),
                "speaker");

        // Headphones: major AV, minor 6.
        assertEquals(BluetoothDeviceClassifier.classify(1048, null, null, null).orElseThrow().type(),
                "headphones");

        // Laptop: major Computer (1), minor 3.
        assertEquals(BluetoothDeviceClassifier.classify(268, null, null, null).orElseThrow().type(),
                "laptop");

        // Phone: major Phone (2), minor 3.
        assertEquals(BluetoothDeviceClassifier.classify(524, null, null, null).orElseThrow().type(),
                "phone");

        // Uncategorized AV (minor 0): no classification.
        assertTrue(BluetoothDeviceClassifier.classify(1024, null, null, null).isEmpty());
    }

    @Test
    public void testServiceUuids() {
        assertEquals(BluetoothDeviceClassifier
                .classify(null, null, List.of(HID), null).orElseThrow().type(), "input_device");

        BluetoothDeviceClassifier.Classification audio = BluetoothDeviceClassifier
                .classify(null, null, List.of(A2DP_SINK), null).orElseThrow();
        assertEquals(audio.type(), "audio");
        assertEquals(audio.source(), "service_uuid");

        // Hands-free gateway role -> phone (audio gear advertises sink/source instead).
        assertEquals(BluetoothDeviceClassifier
                .classify(null, null, List.of(HFP_AG), null).orElseThrow().type(), "phone");

        // Known vendor service (Sonos).
        assertEquals(BluetoothDeviceClassifier
                .classify(null, null, List.of(SONOS), null).orElseThrow().type(), "audio");

        // Private/non-standard UUID: no classification.
        assertTrue(BluetoothDeviceClassifier.classify(
                null, null, List.of("3e1d50cd-7e3e-427d-8e1c-b78aa87fe624"), null).isEmpty());
    }

    @Test
    public void testAppearance() {
        // 192 = Watch (observed on live devices).
        assertEquals(BluetoothDeviceClassifier.classify(null, 192, null, null).orElseThrow().type(),
                "wearable");

        // 962 = HID / Mouse (observed on live devices).
        assertEquals(BluetoothDeviceClassifier.classify(null, 962, null, null).orElseThrow().type(),
                "input_device");

        // 384 = Remote control (observed on live devices).
        assertEquals(BluetoothDeviceClassifier.classify(null, 384, null, null).orElseThrow().type(),
                "input_device");

        assertEquals(BluetoothDeviceClassifier.classify(null, 192, null, null).orElseThrow().source(),
                "appearance");
    }

    @Test
    public void testPrecedenceAndSkips() {
        // Class of Device wins over service UUIDs.
        BluetoothDeviceClassifier.Classification tv = BluetoothDeviceClassifier
                .classify(533564, null, List.of(A2DP_SINK), null).orElseThrow();
        assertEquals(tv.type(), "tv");
        assertEquals(tv.source(), "cod");

        // Devices already identified by a dedicated detector are left alone.
        assertTrue(BluetoothDeviceClassifier.classify(533564, 192, List.of(A2DP_SINK),
                Map.of("samsung_smarttag", Map.of())).isEmpty());
        assertTrue(BluetoothDeviceClassifier.classify(null, null, List.of(A2DP_SINK),
                Map.of("apple_find_my_paired", Map.of("battery", 80))).isEmpty());

        // Nothing known: no classification.
        assertTrue(BluetoothDeviceClassifier.classify(null, null, null, null).isEmpty());
        assertTrue(BluetoothDeviceClassifier.classify(0, 0, List.of(), Map.of()).isEmpty());
    }

}
