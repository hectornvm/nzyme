package app.nzyme.core.rest.responses.bluetooth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class MonitoredBluetoothSignatureListResponse {

    @JsonProperty("signatures")
    public abstract List<MonitoredBluetoothSignatureResponse> signatures();

    public static MonitoredBluetoothSignatureListResponse create(List<MonitoredBluetoothSignatureResponse> signatures) {
        return builder()
                .signatures(signatures)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_MonitoredBluetoothSignatureListResponse.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder signatures(List<MonitoredBluetoothSignatureResponse> signatures);

        public abstract MonitoredBluetoothSignatureListResponse build();
    }
}
