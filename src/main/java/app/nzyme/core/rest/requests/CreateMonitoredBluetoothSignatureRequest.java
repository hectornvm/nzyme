package app.nzyme.core.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@AutoValue
public abstract class CreateMonitoredBluetoothSignatureRequest {

    @NotEmpty
    public abstract String signature();

    @NotEmpty
    public abstract String name();

    @NotNull
    public abstract UUID organizationId();

    @NotNull
    public abstract UUID tenantId();

    @JsonCreator
    public static CreateMonitoredBluetoothSignatureRequest create(@NotEmpty @JsonProperty("signature") String signature,
                                                                  @NotEmpty @JsonProperty("name") String name,
                                                                  @NotNull @JsonProperty("organization_id") UUID organizationId,
                                                                  @NotNull @JsonProperty("tenant_id") UUID tenantId) {
        return builder()
                .signature(signature)
                .name(name)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_CreateMonitoredBluetoothSignatureRequest.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder signature(@NotEmpty String signature);

        public abstract Builder name(@NotEmpty String name);

        public abstract Builder organizationId(@NotNull UUID organizationId);

        public abstract Builder tenantId(@NotNull UUID tenantId);

        public abstract CreateMonitoredBluetoothSignatureRequest build();
    }
}
