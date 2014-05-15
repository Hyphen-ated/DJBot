package hyphenated.djbot;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path("djbot")
public class DjResource {

    private DjBot bot;

    public DjResource(DjConfiguration conf) {
        bot = new DjBot(conf);
    }

    public DjBot getBot() {
        return bot;
    }

    @GET
    @Path("check")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback) {
        return callback + "({\"channel\":\"" + bot.getChannel() + "\",\"volume\":\"" + bot.getVolume() + "\",\"skipid\":\"" + bot.getSongToSkip() + "\"})";
    }
    @GET
    @Path("updatevolume")
    @Produces("application/json")
    public String webVolume(@QueryParam("callback") String callback, @QueryParam("volume") String volume) {
        bot.setVolume(volume);
        return callback + "({\"channel\":\"" + bot.getChannel() + "\",\"success\":true})";
    }

    @GET
    @Path("current")
    @Produces("application/json")
    public String webCurrent(@QueryParam("callback") String callback) {
        SongEntry curSong = bot.startCurrentSong();
        String json = curSong != null ? curSong.toJsonString() : "";
        if(StringUtils.isEmpty(callback)) {
            return json;
        } else {
            return callback + "(" + json + ")";
        }
    }

    @GET
    @Path("next")
    @Produces("application/json")
    public String webNext(@QueryParam("callback") String callback, @QueryParam("idToSkip") String idToSkip) {
        if(bot.noMoreSongs()) {
            return callback + "({\"status\":\"failure\"})";
        }

        if(!StringUtils.isEmpty(idToSkip)) {
            if(! (idToSkip.equals(bot.getCurrentSong().getRequestId()))) {
                //we're trying to skip something that already ended or got skipped
                return callback + "({\"status\":\"failure\"})";
            }
        }
        SongEntry song = bot.nextSong();

        //todo: clean this up, we're not really giving accurate statuses
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
