package app.nzyme.core.bluetooth.sig;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.connect.ConnectRegistryKeys;
import app.nzyme.core.util.MetricNames;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothSigService {

    private static final Logger LOG = LogManager.getLogger(BluetoothSigService.class);

    private final NzymeNode nzyme;
    private final Timer companyIdLookupTimer;
    private final Timer serviceUuidLookupTimer;

    private final ScheduledExecutorService refresher;

    private final ReentrantLock lock = new ReentrantLock();
    private Map<Integer, String> companyIds;
    private Map<String, String> serviceUuids;

    // Can be disabled if Connect is not set up or BT SIG data source is not enabled in Connect.
    private boolean isEnabled = false;

    public BluetoothSigService(NzymeNode nzyme) {
        this.nzyme = nzyme;
        this.companyIdLookupTimer = nzyme.getMetrics()
                .timer(MetricRegistry.name(MetricNames.BTSIG_CID_LOOKUP_TIMING));
        this.serviceUuidLookupTimer = nzyme.getMetrics()
                .timer(MetricRegistry.name(MetricNames.BTSIG_SUUID_LOOKUP_TIMING));

        // Reload on configuration change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_API_KEY.key(), this::reload);
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.CONNECT_ENABLED.key(), this::reload);

        // Reload if provided services by Connect change.
        nzyme.getRegistryChangeMonitor()
                .onChange("core", ConnectRegistryKeys.PROVIDED_SERVICES.key(), this::reload);

        refresher = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("btsig-refresher-%d")
                        .build()
        );

        refresher.scheduleAtFixedRate(this::reload, 1, 1, TimeUnit.HOURS);
    }

    private void reload() {
        // Reload with new registry settings.
        initialize();
    }

    public void initialize() {
        // IMPORTANT: This method will also be called on configuration changes.

        this.isEnabled = nzyme.getConnect().isEnabled();
        if (!this.isEnabled) {
            // Connect is not configured. Fall back to local Bluetooth SIG data
            // files in <data_directory> if present (company-identifiers.txt and
            // service-uuids.txt).
            loadLocalSigData();
            return;
        }

        lock.lock();

        try {
            Optional<Map<Integer, String>> companyIds = fetchCompanyIdsFromConnect();
            Optional<Map<String, String>> serviceUuids = fetchServiceUuidsFromConnect();

            // Check if BT SIG data was disabled in Connect for this cluster. (It's enough to check for one type)
            if (companyIds.isEmpty()) {
                this.isEnabled = false;
                return;
            }

            this.companyIds = companyIds.get();
            this.serviceUuids = serviceUuids.get();
            this.isEnabled = true;
        } catch (Exception e) {
            LOG.error("Could not download Bluetooth SIG data from Connect.", e);
            this.isEnabled = false;
        } finally {
            lock.unlock();
        }
    }

    private void loadLocalSigData() {
        lock.lock();
        try {
            Map<Integer, String> loadedCompanies = new HashMap<>();

            // Company identifiers: <decimal-id>\t<Company Name> per line.
            Path companyPath = Paths.get(nzyme.getBaseConfiguration().dataDirectory(), "company-identifiers.txt");
            if (Files.exists(companyPath)) {
                for (String rawLine : Files.readAllLines(companyPath)) {
                    String line = rawLine.trim();
                    int tab = line.indexOf('\t');
                    if (tab <= 0) {
                        continue;
                    }

                    try {
                        int id = Integer.parseInt(line.substring(0, tab));
                        String name = line.substring(tab + 1).trim();
                        if (!name.isEmpty()) {
                            loadedCompanies.put(id, name);
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip malformed lines.
                    }
                }
            } else {
                LOG.debug("No local company identifiers file found at [{}].", companyPath);
            }

            this.companyIds = loadedCompanies;
            this.serviceUuids = loadLocalServiceUuids();
            this.isEnabled = !loadedCompanies.isEmpty() || !this.serviceUuids.isEmpty();
            if (this.isEnabled) {
                LOG.info("Loaded [{}] Bluetooth company identifiers and [{}] service UUIDs from local files.", loadedCompanies.size(), this.serviceUuids.size());
            }
        } catch (Exception e) {
            LOG.error("Could not load local Bluetooth SIG data.", e);
            this.isEnabled = false;
        } finally {
            lock.unlock();
        }
    }

    private Map<String, String> loadLocalServiceUuids() {
        Map<String, String> loaded = new HashMap<>();

        // Service UUIDs: <key>\t<Service Name> per line (key matches the 16-bit
        // extract format, e.g. "0x180F").
        Path path = Paths.get(nzyme.getBaseConfiguration().dataDirectory(), "service-uuids.txt");
        if (!Files.exists(path)) {
            LOG.debug("No local service UUID file found at [{}].", path);
            return loaded;
        }

        try {
            for (String rawLine : Files.readAllLines(path)) {
                String line = rawLine.trim();
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }

                String key = line.substring(0, tab).trim();
                String name = line.substring(tab + 1).trim();
                if (!key.isEmpty() && !name.isEmpty()) {
                    // Keys must match extract16BitUuid() output exactly (e.g. "0x180F" -
                    // lowercase 'x', uppercase hex). No case normalisation here.
                    loaded.put(key, name);
                }
            }
        } catch (Exception e) {
            LOG.error("Could not load local service UUID data.", e);
        }

        if (!loaded.isEmpty()) {
            LOG.info("Loaded [{}] Bluetooth service UUIDs from local file [{}].", loaded.size(), path);
        }

        return loaded;
    }

    public Optional<String> lookupCompanyId(int companyId) {
        if (!isEnabled) {
            return Optional.empty();
        }

        try(Timer.Context ignored = companyIdLookupTimer.time()) {
            lock.lock();

            try {
                String cid = companyIds.get(companyId);
                return cid == null ? Optional.empty() : Optional.of(cid);
            } finally {
                lock.unlock();
            }
        }
    }

    public Optional<String> lookupServiceUuid(String uuid) {
        if (!isEnabled) {
            return Optional.empty();
        }

        try(Timer.Context ignored = serviceUuidLookupTimer.time()) {
            lock.lock();

            try {
                String name = serviceUuids.get(uuid);
                return name == null ? Optional.empty() : Optional.of(name);
            } finally {
                lock.unlock();
            }
        }
    }

    private Optional<Map<Integer, String>> fetchCompanyIdsFromConnect() {
        LOG.debug("Loading new Bluetooth SIG Company IDs from Connect.");

        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .followRedirects(true)
                    .build();

            HttpUrl url = HttpUrl.get(nzyme.getConnect().getApiUri())
                    .newBuilder()
                    .addPathSegment("data")
                    .addPathSegment("bluetooth")
                    .addPathSegment("companyids")
                    .build();

            Response response = c.newCall(new Request.Builder()
                    .addHeader("User-Agent", "nzyme")
                    .get()
                    .url(url)
                    .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nzyme.getConnect().getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader(HttpHeaders.USER_AGENT, "nzyme-node")
                    .build()
            ).execute();

            try (response) {
                if (!response.isSuccessful()) {
                    if (response.code() == 403) {
                        // BG SIG data disabled in Connect for this cluster.
                        return Optional.empty();
                    }

                    throw new RuntimeException("Expected HTTP 200 or 403 but got HTTP " + response.code());
                }


                if (response.body() == null) {
                    throw new RuntimeException("Empty response.");
                }

                LOG.debug("Bluetooth SIG Company ID data download from Connect complete.");

                ObjectMapper om = JsonMapper.builder()
                        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();

                ConnectCompanyIdListResponse ids = om.readValue(response.body().bytes(), ConnectCompanyIdListResponse.class);

                Map<Integer, String> table = Maps.newHashMap();
                for (ConnectCompanyIdResponse id : ids.companyIds()) {
                    table.put(id.companyId(), id.name());
                }

                return Optional.of(table);
            }
        } catch (Exception e) {
            LOG.error("Could not download SIG Company ID data from Connect.", e);
            return Optional.empty();
        }
    }

    private Optional<Map<String, String>> fetchServiceUuidsFromConnect() {
        LOG.debug("Loading new Bluetooth SIG service UUIDs from Connect.");

        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .followRedirects(true)
                    .build();

            HttpUrl url = HttpUrl.get(nzyme.getConnect().getApiUri())
                    .newBuilder()
                    .addPathSegment("data")
                    .addPathSegment("bluetooth")
                    .addPathSegment("serviceuuids")
                    .build();

            Response response = c.newCall(new Request.Builder()
                    .addHeader("User-Agent", "nzyme")
                    .get()
                    .url(url)
                    .addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nzyme.getConnect().getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader(HttpHeaders.USER_AGENT, "nzyme-node")
                    .build()
            ).execute();

            try (response) {
                if (!response.isSuccessful()) {
                    if (response.code() == 403) {
                        // BG SIG data disabled in Connect for this cluster.
                        return Optional.empty();
                    }

                    throw new RuntimeException("Expected HTTP 200 or 403 but got HTTP " + response.code());
                }


                if (response.body() == null) {
                    throw new RuntimeException("Empty response.");
                }

                LOG.debug("Bluetooth SIG service UUID data download from Connect complete.");

                ObjectMapper om = JsonMapper.builder()
                        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();

                ConnectServiceUuidListResponse ids = om.readValue(response.body().bytes(), ConnectServiceUuidListResponse.class);

                Map<String, String> table = Maps.newHashMap();
                for (ConnectServiceUuidResponse id : ids.serviceUuids()) {
                    table.put(id.uuid(), id.name());
                }

                return Optional.of(table);
            }
        } catch (Exception e) {
            LOG.error("Could not download SIG service UUID data from Connect.", e);
            return Optional.empty();
        }
    }

}
