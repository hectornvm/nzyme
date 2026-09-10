package app.nzyme.core.rest.responses.bluetooth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class MonitoredBluetoothSignatureResponse {

    @JsonProperty("uuid")
    public abstract UUID uuid();

    @JsonProperty("signature")
    public abstract String signature();

    @JsonProperty("name")
    public abstract String name();

    @JsonProperty("organization_id")
    @Nullable
    public abstract UUID organizationId();

    @JsonProperty("tenant_id")
    @Nullable
    public abstract UUID tenantId();

    @JsonProperty("created_at")
    public abstract DateTime createdAt();

    @JsonProperty("is_alerted")
    public abstract boolean isAlerted();

    public static MonitoredBluetoothSignatureResponse create(UUID uuid,
                                                             String signature,
                                                             String name,
                                                             UUID organizationId,
                                                             UUID tenantId,
                                                             DateTime createdAt,
                                                             boolean isAlerted) {
        return builder()
                .uuid(uuid)
                .signature(signature)
                .name(name)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .createdAt(createdAt)
                .isAlerted(isAlerted)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitoredBluetoothSignatureResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder signature(String signature);

        public abstract Builder name(String name);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder isAlerted(boolean isAlerted);

        public abstract MonitoredBluetoothSignatureResponse build();
    }
}
