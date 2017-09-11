package hyphenated.djbot.db;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface DbMetaDAO {
    @SqlQuery("select 1 from sqlite_master where type='table' and name='zmeta'")
    boolean metaTableExists();

    @SqlQuery("select 1 from sqlite_master where type='table' and name='songqueue'")
    boolean songqueueTableExists();

    @SqlUpdate("create table if not exists zmeta(k text primary key, v text)")
    void createMetaTable();

    @SqlUpdate("insert into zmeta (k, v) values('version', :version)")
    void insertDbVersion(@Bind("version")int version);

    @SqlUpdate("update zmeta set v = :version where k='version' ")
    void updateDbVersion(@Bind("version")int version);

    @SqlUpdate("alter table songqueue add column site text")
    void addSiteColumn();

    @SqlUpdate("create table if not exists songqueue(requestId int primary key, title text, videoId text, user text," +
            " requestTime long, durationSeconds int, backup bool, startSeconds int, toBePlayed boolean, liked boolean, site text) ")
    void createSongqueueTable();

    @SqlUpdate("update songqueue set site='sc' where videoId like '/%'")
    void populateLegacySoundcloudIds();
    
    @SqlUpdate("update songqueue set site='yt' where videoId not like '/%'")
    void populateLegacyYoutubeIds();
}
