package app.nzyme.core.rest.resources.bluetooth;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.bluetooth.db.MonitoredBluetoothSignature;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.requests.CreateMonitoredBluetoothSignatureRequest;
import app.nzyme.core.rest.responses.bluetooth.MonitoredBluetoothSignatureListResponse;
import app.nzyme.core.rest.responses.bluetooth.MonitoredBluetoothSignatureResponse;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for monitored Bluetooth device signatures (Plan C2).
 *
 * A monitored signature is the rotation-stable identity of a physical device (Plan A signature
 * grouping), not a MAC address. Raising of PRESENT / SIGNATURE_MISMATCH alerts happens in
 * BluetoothTable (event-driven) and MonitoredBluetoothDeviceMonitor (periodic).
 *
 * LOCAL ONLY feature (BT attribution work - never push upstream).
 */
@Path("/api/bluetooth/monitored")
@Produces(MediaType.APPLICATION_JSON)
public class BluetoothMonitoredSignaturesResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "bluetooth_monitoring_manage" })
    public Response findAll(@Context SecurityContext sc,
                            @QueryParam("organization_id") @NotNull UUID organizationId,
                            @QueryParam("tenant_id") @NotNull UUID tenantId) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        List<MonitoredBluetoothSignatureResponse> signatures = Lists.newArrayList();
        for (MonitoredBluetoothSignature signature : nzyme.getBluetooth()
                .findAllMonitoredSignatures(organizationId, tenantId)) {
            signatures.add(toResponse(signature));
        }

        return Response.ok(MonitoredBluetoothSignatureListResponse.create(signatures)).build();
    }

    @GET
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "bluetooth_monitoring_manage" })
    @Path("/show/{uuid}")
    public Response findOne(@Context SecurityContext sc, @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredBluetoothSignature> result = nzyme.getBluetooth().findMonitoredSignature(uuid);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, result.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(toResponse(result.get())).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "bluetooth_monitoring_manage" })
    public Response create(@Context SecurityContext sc,
                           @Valid CreateMonitoredBluetoothSignatureRequest req) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String signature = req.signature().trim().toLowerCase();
        if (!signature.matches("[0-9a-f]{64}")) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (nzyme.getBluetooth().findMonitoredSignatureBySignature(
                req.organizationId(), req.tenantId(), signature).isPresent()) {
            // Already monitoring this signature for this tenant.
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        nzyme.getBluetooth().registerMonitoredSignature(
                req.organizationId(),
                req.tenantId(),
                signature,
                req.name()
        );

        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "bluetooth_monitoring_manage" })
    @Path("/show/{uuid}")
    public Response delete(@Context SecurityContext sc, @PathParam("uuid") UUID uuid) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MonitoredBluetoothSignature> result = nzyme.getBluetooth().findMonitoredSignature(uuid);

        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!entityAccessible(authenticatedUser, result.get())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getBluetooth().deleteMonitoredSignature(uuid);

        return Response.ok().build();
    }

    private MonitoredBluetoothSignatureResponse toResponse(MonitoredBluetoothSignature signature) {
        boolean isAlerted = !nzyme.getDetectionAlertService()
                .findAllActiveAlertsOfMonitoredNetwork(signature.uuid())
                .isEmpty();

        return MonitoredBluetoothSignatureResponse.create(
                signature.uuid(),
                signature.signature(),
                signature.name(),
                signature.organizationId(),
                signature.tenantId(),
                signature.createdAt(),
                isAlerted
        );
    }

}
