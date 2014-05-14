package hyphenated.djbot;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.joda.time.DateTime;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.xml.ws.Endpoint;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Path("autodj")
public class DjResource {

    private DjBot bot;

    public DjResource(DjConfiguration conf) {
        bot = new DjBot(conf);
    }

    @GET
    @Path("check")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback) {
        return callback + "({\"channel\":\"" + bot.getChannel() + "\",\"volume\":\"" + bot.getVolume() + "\",\"skipid\":\"0\"})";
    }
    @GET
    @Path("updatevolume")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback, @QueryParam("volume") String volume) {
        bot.setVolume(volume);
        return callback + "({\"channel\":\"" + bot.getChannel() + "\",\"success\":true})";
    }

    @GET
    @Path("next")
    @Produces("application/json")
    public String webNext(@QueryParam("callback") String callback) {
        if(bot.noMoreSongs()) {
            return callback + "({\"status\":\"failure\"})";
        }

        SongEntry song = bot.nextSong();

        if(song == null) {
            return callback + "({\"status\":\"failure\"})";
        }

        JSONObject resp = new JSONObject();
        resp.put("status", "success");
        resp.put("noNewSong", "false");

        JSONObject nextSongObj = new JSONObject();
        nextSongObj.put("vid", song.getVideoId());
        nextSongObj.put("url", song.buildYoutubeUrl());
        nextSongObj.put("id", song.getRequestId());
        nextSongObj.put("user", song.getUser());
        nextSongObj.put("type", "youtube");
        nextSongObj.put("title", song.getTitle());

        resp.put("next", nextSongObj);
        return callback + "(" + resp.toString() + ")";
    }


}
