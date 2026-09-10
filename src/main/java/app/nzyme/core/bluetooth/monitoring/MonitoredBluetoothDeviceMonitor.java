package app.nzyme.core.bluetooth.monitoring;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.bluetooth.db.MonitoredBluetoothSignature;
import app.nzyme.core.bluetooth.db.MonitoredBluetoothSignatureSighting;
import app.nzyme.core.detection.alerts.DetectionType;
import app.nzyme.core.periodicals.Periodical;
import app.nzyme.core.security.authentication.db.OrganizationEntry;
import app.nzyme.core.security.authentication.db.TenantEntry;
import app.nzyme.plugin.Subsystem;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Periodic impersonation heuristic for monitored Bluetooth signatures (Plan C3,
 * SIGNATURE_MISMATCH).
 *
 * A signature groups address rotations of one physical device, so multiple MACs advertising the
 * same signature is normal. The suspicious case is multiple MACs (or taps) advertising the same
 * signature AT THE SAME TIME at strongly conflicting signal strengths: one physical device cannot
 * be simultaneously near and far, so this indicates two devices sharing one signature - a likely
 * clone/impersonation.
 *
 * Heuristic (fixed constants, revisit after observing real-world data):
 *  - aggregate sightings of each monitored signature per (MAC, tap) over LOOKBACK_MINUTES
 *  - require at least 2 sightings per group to smooth advertisement RSSI noise
 *  - keep only groups seen within CONCURRENT_WINDOW_MINUTES (i.e. actually concurrent, which
 *    filters out sequential MAC rotations of a device that simply moved through the house)
 *  - alert when at least 2 remaining groups differ by SIGNAL_SPREAD_THRESHOLD_DBM or more
 *
 * LOCAL ONLY feature (BT attribution work - never push upstream).
 */
public class MonitoredBluetoothDeviceMonitor extends Periodical {

    private static final Logger LOG = LogManager.getLogger(MonitoredBluetoothDeviceMonitor.class);

    public static final int LOOKBACK_MINUTES = 15;
    public static final int CONCURRENT_WINDOW_MINUTES = 2;
    public static final int SIGNAL_SPREAD_THRESHOLD_DBM = 25;

    private final NzymeNode nzyme;

    public MonitoredBluetoothDeviceMonitor(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected void execute() {
        LOG.debug("Starting Bluetooth monitored-device monitor run.");

        for (OrganizationEntry org : nzyme.getAuthenticationService().findAllOrganizations()) {
            for (TenantEntry tenant : nzyme.getAuthenticationService().findAllTenantsOfOrganization(org.uuid())) {
                for (MonitoredBluetoothSignature monitored : nzyme.getBluetooth()
                        .findAllMonitoredSignatures(org.uuid(), tenant.uuid())) {
                    checkSignature(monitored);
                }
            }
        }
    }

    private void checkSignature(MonitoredBluetoothSignature monitored) {
        List<MonitoredBluetoothSignatureSighting> sightings = nzyme.getBluetooth()
                .findRecentMonitoredSignatureSightings(
                        monitored.signature(),
                        monitored.organizationId(),
                        monitored.tenantId(),
                        LOOKBACK_MINUTES
                ).stream()
                .filter(s -> s.lastSeen().isAfter(
                        DateTime.now().minusMinutes(CONCURRENT_WINDOW_MINUTES)))
                .collect(Collectors.toList());

        if (sightings.size() < 2) {
            return;
        }

        double minRssi = sightings.stream()
                .mapToDouble(MonitoredBluetoothSignatureSighting::averageRssi)
                .min()
                .getAsDouble();
        double maxRssi = sightings.stream()
                .mapToDouble(MonitoredBluetoothSignatureSighting::averageRssi)
                .max()
                .getAsDouble();
        double spread = maxRssi - minRssi;

        if (spread < SIGNAL_SPREAD_THRESHOLD_DBM) {
            return;
        }

        // Use the strongest sighting as context for this signature-level alert.
        MonitoredBluetoothSignatureSighting strongest = sightings.stream()
                .max((a, b) -> Double.compare(a.averageRssi(), b.averageRssi()))
                .get();

        Map<String, String> attributes = Maps.newHashMap();
        attributes.put("signature", monitored.signature());
        attributes.put("monitored_signature_name", monitored.name());
        attributes.put("macs", sightings.stream()
                .map(MonitoredBluetoothSignatureSighting::mac)
                .collect(Collectors.joining(", ")));
        attributes.put("taps", sightings.stream()
                .map(MonitoredBluetoothSignatureSighting::tapName)
                .distinct()
                .collect(Collectors.joining(", ")));
        attributes.put("rssi_spread_dbm", String.format("%.1f", spread));
        attributes.put("signal_strengths", sightings.stream()
                .map(s -> String.format("%s: %.1f dBm", s.mac(), s.averageRssi()))
                .collect(Collectors.joining(", ")));

        nzyme.getDetectionAlertService().raiseAlert(
                monitored.organizationId(),
                monitored.tenantId(),
                monitored.uuid(),
                strongest.tapId(),
                DetectionType.BLUETOOTH_MONITORED_DEVICE_SIGNATURE_MISMATCH,
                Subsystem.BLUETOOTH,
                "Monitored Bluetooth device \"" + monitored.name() + "\" was seen concurrently with " +
                        "conflicting signal strengths (" + String.format("%.1f", spread) + " dBm spread). " +
                        "Possible impersonation.",
                attributes,
                Set.of("signature")
        );
    }

    @Override
    public String getName() {
        return "Bluetooth Monitored Device Monitor";
    }

}
