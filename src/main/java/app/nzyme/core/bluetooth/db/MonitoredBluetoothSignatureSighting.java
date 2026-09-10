package app.nzyme.core.bluetooth.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.UUID;

/**
 * Aggregated recent sighting of a monitored Bluetooth signature by one MAC address on one tap.
 * Used by the SIGNATURE_MISMATCH impersonation heuristic (Plan C3): multiple concurrent
 * sightings of the same signature at strongly conflicting signal strengths indicate that two
 * physical devices advertise the same signature (i.e. a possible clone).
 *
 * LOCAL ONLY feature (BT attribution work - never push upstream).
 */
@AutoValue
public abstract class MonitoredBluetoothSignatureSighting {

    public abstract String mac();
    public abstract UUID tapId();
    public abstract String tapName();
    public abstract double averageRssi();
    public abstract long sightings();
    public abstract DateTime lastSeen();

    public static MonitoredBluetoothSignatureSighting create(String mac,
                                                             UUID tapId,
                                                             String tapName,
                                                             double averageRssi,
                                                             long sightings,
                                                             DateTime lastSeen) {
        return builder()
                .mac(mac)
                .tapId(tapId)
                .tapName(tapName)
                .averageRssi(averageRssi)
                .sightings(sightings)
                .lastSeen(lastSeen)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitoredBluetoothSignatureSighting.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder mac(String mac);

        public abstract Builder tapId(UUID tapId);

        public abstract Builder tapName(String tapName);

        public abstract Builder averageRssi(double averageRssi);

        public abstract Builder sightings(long sightings);

        public abstract Builder lastSeen(DateTime lastSeen);

        public abstract MonitoredBluetoothSignatureSighting build();
    }
}
