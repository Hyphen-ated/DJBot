package hyphenated.djbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path("djbot")
public class DjResource {

    private DjService bot;

    public DjResource(DjConfiguration conf) {
        DjIrcBot irc = new DjIrcBot(conf);
        bot = new DjService(conf, irc);
        irc.setDjService(bot);
        irc.startup();
    }

    public DjService getBot() {
        return bot;
    }

    @GET
    @Path("check")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback) {
        if(bot.getCurrentSong() == null) {
            return "";
        }
        return callback + "({\"channel\":\"" + bot.getStreamer() + "\",\"volume\":\"" + bot.getVolume() + "\",\"id\":\"" + bot.getCurrentSong().getRequestId() + "\"})";
    }
    @GET
    @Path("updatevolume")
    @Produces("application/json")
    public String webVolume(@QueryParam("callback") String callback, @QueryParam("volume") String volume) {
        bot.setVolume(volume);
        return callback + "({\"channel\":\"" + bot.getStreamer() + "\",\"success\":true})";
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
        SongEntry song;
        synchronized (bot) {
            if(bot.noMoreSongs()) {
                return callback + "({\"status\":\"failure\"})";
            }

            if(!StringUtils.isEmpty(idToSkip)) {
                if(! (idToSkip.equals(bot.getCurrentSong().getRequestId()))) {
                    //we're trying to skip something that already ended or got skipped
                    return callback + "({\"status\":\"failure\"})";
                }
            }
            song = bot.nextSong();
        }

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

    @GET
    @Path("info")
    @Produces("application/json")
    public String webInfo(@QueryParam("callback") String callback) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bot.getStateRepresentation());
    }


}
