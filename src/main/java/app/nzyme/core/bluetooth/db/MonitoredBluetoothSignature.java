package app.nzyme.core.bluetooth.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.UUID;

/**
 * A user-registered Bluetooth device signature the operator wants to watch for.
 * When a device advertising this signature is seen, a PRESENT alert is raised.
 * Multiple MAC rotations of the same physical device share one signature (see
 * BluetoothTable.computeSignature / Plan A), so watching a signature is the
 * right granularity for a physical device.
 *
 * LOCAL ONLY feature (BT attribution work - never push upstream).
 */
@AutoValue
public abstract class MonitoredBluetoothSignature {

    public abstract long id();
    public abstract UUID uuid();
    public abstract String signature();
    public abstract String name();
    public abstract UUID organizationId();
    public abstract UUID tenantId();
    public abstract DateTime createdAt();

    public static MonitoredBluetoothSignature create(long id, UUID uuid, String signature, String name, UUID organizationId, UUID tenantId, DateTime createdAt) {
        return builder()
                .id(id)
                .uuid(uuid)
                .signature(signature)
                .name(name)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitoredBluetoothSignature.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder uuid(UUID uuid);

        public abstract Builder signature(String signature);

        public abstract Builder name(String name);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract MonitoredBluetoothSignature build();
    }
}
