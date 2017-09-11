package hyphenated.djbot.db;


import hyphenated.djbot.json.SongEntry;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@RegisterMapper(SongEntryMapper.class)
public interface SongQueueDAO {
    @SqlUpdate("insert into songqueue " +
            "(requestId,  title,  videoId,  user,  requestTime,  durationSeconds,  backup,  startSeconds, toBePlayed, liked, site)" +
            "values(:requestId, :title, :videoId, :user, :requestTime, :durationSeconds, :backup, :startSeconds, :toBePlayed, 0, :site)")
    void addSong(@BindBean SongEntry song, @Bind("toBePlayed") boolean toBePlayed);

    @SqlBatch("insert into songqueue " +
            "(requestId,  title,  videoId,  user,  requestTime,  durationSeconds,  backup,  startSeconds, toBePlayed, liked, site)" +
            "values(:requestId, :title, :videoId, :user, :requestTime, :durationSeconds, :backup, :startSeconds, 0, 0, :site)")
    @BatchChunkSize(1000)
    void addSongs(@BindBean Iterator<SongEntry> songs);

    @SqlUpdate("update songqueue set liked = :val where requestId = :id")
    void setSongLiked(@Bind("id") int id, @Bind("val") boolean val);

    @SqlQuery("select count(*) from songqueue where user=:user and liked=1")
    int getUserScore(@Bind("user") String user);
    
    @SqlUpdate("update songqueue set toBePlayed = :val where requestId = :id")
    void setSongToBePlayed(@Bind("id") int id, @Bind("val") boolean val);

    @SqlQuery("select requestId, title, videoId, user, requestTime, durationSeconds, backup, startSeconds, site " +
            "from songqueue ")
    List<SongEntry> getAllSongs();

    @SqlQuery("select requestId, title, videoId, user, requestTime, durationSeconds, backup, startSeconds, site " +
            "from songqueue " +
            "where requestTime > :historyCutoff ")
    List<SongEntry> getSongsAfterDate(@Bind("historyCutoff") long historyCutoff);

    @SqlQuery("select requestId, title, videoId, user, requestTime, durationSeconds, backup, startSeconds, site " +
            "from songqueue " +
            "where toBePlayed = 1 ")
    List<SongEntry> getSongsToPlay();

    @SqlQuery("select max(requestId) from songqueue")
    int getHighestId();
    
}
