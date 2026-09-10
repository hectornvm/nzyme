package app.nzyme.core.bluetooth.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class MonitoredBluetoothSignatureSightingMapper implements RowMapper<MonitoredBluetoothSignatureSighting> {

    @Override
    public MonitoredBluetoothSignatureSighting map(ResultSet rs, StatementContext ctx) throws SQLException {
        return MonitoredBluetoothSignatureSighting.create(
                rs.getString("mac"),
                UUID.fromString(rs.getString("tap_id")),
                rs.getString("tap_name"),
                rs.getDouble("average_rssi"),
                rs.getLong("sightings"),
                new DateTime(rs.getTimestamp("last_seen"))
        );
    }

}
