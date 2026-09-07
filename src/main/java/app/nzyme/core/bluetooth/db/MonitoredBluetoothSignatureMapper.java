package app.nzyme.core.bluetooth.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class MonitoredBluetoothSignatureMapper implements RowMapper<MonitoredBluetoothSignature> {

    @Override
    public MonitoredBluetoothSignature map(ResultSet rs, StatementContext ctx) throws SQLException {
        return MonitoredBluetoothSignature.create(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("signature"),
                rs.getString("name"),
                rs.getString("organization_id") == null ? null
                        : UUID.fromString(rs.getString("organization_id")),
                rs.getString("tenant_id") == null ? null
                        : UUID.fromString(rs.getString("tenant_id")),
                new DateTime(rs.getTimestamp("created_at"))
        );
    }

}
