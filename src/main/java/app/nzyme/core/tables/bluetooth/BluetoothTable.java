package app.nzyme.core.tables.bluetooth;

import app.nzyme.core.bluetooth.db.BluetoothServiceUuidJson;
import app.nzyme.core.bluetooth.sig.AppleManufacturerData;
import app.nzyme.core.rest.resources.taps.reports.tables.bluetooth.BluetoothDeviceReport;
import app.nzyme.core.rest.resources.taps.reports.tables.bluetooth.BluetoothDevicesReport;
import app.nzyme.core.tables.DataTable;
import app.nzyme.core.tables.TablesService;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.Timer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class BluetoothTable implements DataTable {

    private static final Logger LOG = LogManager.getLogger(BluetoothTable.class);

    private final TablesService tablesService;

    private final Timer totalReportTimer;
    private final ObjectMapper om;

    public BluetoothTable(TablesService tablesService) {
        this.tablesService = tablesService;
        this.om = new ObjectMapper();

        this.totalReportTimer = tablesService.getNzyme().getMetrics()
                .timer(MetricNames.BLUETOOTH_TOTAL_REPORT_PROCESSING_TIMER);
    }

    public void handleReport(UUID tapUuid, DateTime timestamp, BluetoothDevicesReport report) {
        tablesService.getNzyme().getDatabase().useHandle(handle -> {
            try(Timer.Context ignored = totalReportTimer.time()) {
                writeDevices(handle, tapUuid, report.devices());
            }
        });
    }

    private void writeDevices(Handle handle, UUID tapUuid, List<BluetoothDeviceReport> devices) {
        PreparedBatch batch = handle.prepareBatch("INSERT INTO bluetooth_devices(uuid, tap_uuid, mac, oui, " +
                "alias, device, transport, name, rssi, company_id, class_number, appearance, modalias, tx_power, " +
                "manufacturer_data, manufacturer_name, uuids, service_data, tags, signature, address_type, " +
                "last_seen, created_at) " +
                "VALUES(:uuid, :tap_uuid, :mac, :oui, :alias, :device, :transport, :name, :rssi, :company_id, " +
                ":class_number, :appearance, :modalias, :tx_power, :manufacturer_data, :manufacturer_name, " +
                ":uuids, :service_data, :tags::jsonb, :signature, :address_type, :last_seen, NOW())");

        for (BluetoothDeviceReport device : devices) {
            if (device.rssi() == null || device.rssi() == 0) {
                /*
                 * Sometimes devices are reported as a 0 RSSI. Those are usually currently paired devices.
                 */
                continue;
            }

            // OUI.
            Optional<String> oui = tablesService.getNzyme()
                    .getOuiService()
                    .lookup(device.mac());

            // Manufacturer name.
            Optional<String> manufacturerName;
            if (device.companyId() != null) {
                manufacturerName = tablesService.getNzyme()
                        .getBluetoothSigService()
                        .lookupCompanyId(device.companyId());
            } else {
                manufacturerName = Optional.empty();
            }

            List<BluetoothServiceUuidJson> serviceUuids = Lists.newArrayList();
            if (device.uuids() != null) {
                for (String uuid : device.uuids()) {
                    try {
                        serviceUuids.add(BluetoothServiceUuidJson.create(
                                uuid,
                                tablesService.getNzyme().getBluetoothSigService()
                                        .lookupServiceUuid(extract16BitUuid(uuid))
                                        .orElse(null)
                        ));
                    } catch(InvalidBluetoothUuidException e) {
                        LOG.debug("Could not build Bluetooth Service UUID from UUID [{}] for MAC [{}]. " +
                                "Skipping.", uuid, device.mac(), e);
                    }
                }
            }

            String uuids = null;
            String serviceData = null;
            try {
                // Service UUIDs.
                if (!serviceUuids.isEmpty()) {
                    uuids = om.writeValueAsString(serviceUuids);
                }
                serviceData = om.writeValueAsString(device.serviceData());
            } catch (JacksonException e) {
                LOG.warn("Could not serialize Bluetooth device data. Skipping attributes.", e);
            }

            // Merge a node-side Apple advertising classification into the reported
            // tags. The tap only tags FindMy type bytes 0x07/0x12; other Apple
            // payload types go untagged. Classify node-side from the stored
            // manufacturer payload (no tap change). Skip types the tap already tags.
            Map<String, Map<String, Object>> mergedTags = device.tags();
            if (device.companyId() != null && device.companyId() == 76 && device.manufacturerData() != null) {
                Optional<AppleManufacturerData.AppleClassification> apple = AppleManufacturerData.classify(
                        device.companyId(), device.manufacturerData());
                if (apple.isPresent() && apple.get().type() != 0x07 && apple.get().type() != 0x12) {
                    mergedTags = Maps.newHashMap(mergedTags == null ? Maps.newHashMap() : mergedTags);
                    Map<String, Object> attrs = Maps.newHashMap();
                    attrs.put("type", apple.get().typeHex());
                    attrs.put("label", apple.get().label());
                    mergedTags.put("apple_advertising", attrs);
                }
            }

            String tags;
            if (mergedTags != null) {
                try {
                    tags = om.writeValueAsString(mergedTags);
                } catch (JacksonException e) {
                    LOG.error("Could not write reported tags of Bluetooth device [{}] to JSON. Skipping tags.",
                            device.mac(), e);
                    tags = null;
                }
            } else {
                tags = null;
            }

            batch
                    .bind("uuid", UUID.randomUUID())
                    .bind("tap_uuid", tapUuid)
                    .bind("mac", device.mac())
                    .bind("oui", oui)
                    .bind("alias", device.alias())
                    .bind("device", device.device())
                    .bind("transport", device.transport())
                    .bind("name", device.name())
                    .bind("rssi", device.rssi())
                    .bind("company_id", device.companyId())
                    .bind("class_number", device.classNumber())
                    .bind("appearance", device.appearance())
                    .bind("modalias", device.modalias())
                    .bind("tx_power", device.txPower())
                    .bind("manufacturer_data", device.manufacturerData())
                    .bind("manufacturer_name", manufacturerName)
                    .bind("uuids", uuids)
                    .bind("service_data", serviceData)
                    .bind("tags", tags)
                    .bind("signature", computeSignature(device.companyId(), device.uuids(), device.name()))
                    .bind("address_type", device.addressType())
                    .bind("last_seen", device.lastSeen())
                    .add();
        }

        batch.execute();
    }


    private static String computeSignature(Integer companyId, List<String> uuids, String name) {
        String normalizedName = name == null ? "" : name.trim().toLowerCase();

        TreeSet<String> cleanUuids = new TreeSet<>();
        boolean hasProductPrivateUuid = false;
        if (uuids != null) {
            for (String uuid : uuids) {
                if (uuid == null || uuid.isEmpty()) {
                    continue;
                }
                String u = uuid.toLowerCase().trim();
                if (u.length() == 36 && u.startsWith("0000") && u.endsWith("-0000-1000-8000-00805f9b34fb")) {
                    u = u.substring(4, 8);
                } else {
                    hasProductPrivateUuid = true;
                }
                cleanUuids.add(u);
            }
        }

        if (normalizedName.isEmpty() && !hasProductPrivateUuid) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("c=").append(companyId == null ? 0 : companyId).append("|");
        sb.append("u=").append(String.join(",", cleanUuids)).append("|");
        sb.append("n=").append(normalizedName);

        try {
            return toHex(MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String extract16BitUuid(String uuidStr) throws InvalidBluetoothUuidException {
        if (uuidStr == null || uuidStr.isEmpty()) {
            throw new InvalidBluetoothUuidException("UUID is null or empty");
        }

        uuidStr = uuidStr.toUpperCase();

        // Confirm it's a valid UUID
        try {
            UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidBluetoothUuidException("Not a valid UUID");
        }

        // Extract the first 4 characters from the UUID string (the 16-bit UUID part)
        return uuidStr.substring(0, 8).replace("0000", "0x");
    }

    @Override
    public void retentionClean() {
        // NOOP. Remove from plugin APIs if there remains no use. Database cleaned by category/tenant independently.
    }

    public static final class InvalidBluetoothUuidException extends Exception {
        public InvalidBluetoothUuidException(String msg) {
            super(msg);
        }
    }

}
