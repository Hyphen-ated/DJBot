package hyphenated.djbot.db;

import hyphenated.djbot.json.SongEntry;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SongEntryMapper implements ResultSetMapper<SongEntry>
{
    public SongEntry map(int index, ResultSet r, StatementContext ctx) throws SQLException
    {
        return new SongEntry(r.getString("title"), r.getString("videoId"), r.getInt("requestId"),
                r.getString("user"), r.getLong("requestTime"), r.getInt("durationSeconds"),
                r.getBoolean("backup"), r.getInt("startSeconds"));
    }
}

