package tv.ballsofsteel;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

@Path("autodj")
public class WebResource {

    static DjBot bot;

    static {
        try {
            DjConfiguration.init();
            List<String> history = IOUtils.readLines(new FileInputStream(DjConfiguration.queueHistoryFilePath), "utf-8");

            String placeStr = IOUtils.toString(new FileInputStream(DjConfiguration.queuePlaceFilePath), "utf-8");
            int placeInHistory = Integer.parseInt(placeStr);

            ArrayList<SongEntry> songList = new ArrayList<SongEntry>();
            ObjectMapper mapper = new ObjectMapper();
            for(String historyElement : history) {
                SongEntry entry =mapper.readValue(historyElement, SongEntry.class);
                if(entry.getRequestId() >= placeInHistory) {
                    songList.add(entry);
                }
            }


            // Now start our bot up.
            bot = new DjBot(songList, placeInHistory+songList.size());

            // Enable debugging output.
            bot.setVerbose(true);

            // Connect to the IRC server.
            bot.connect("irc.twitch.tv", 6667, DjConfiguration.password);

            bot.joinChannel("#" + DjConfiguration.channel);

        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    @GET
    @Path("check")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback) {
        return callback + "({\"channel\":\"" + DjConfiguration.channel + "\",\"volume\":\"" + bot.volume + "\",\"skipid\":\"0\"})";
    }
    @GET
    @Path("updatevolume")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback, @QueryParam("volume") String volume) {
        bot.setVolume(volume);
        return callback + "({\"channel\":\"" + DjConfiguration.channel + "\",\"success\":true})";
    }

    @GET
    @Path("next")
    @Produces("application/json")
    public String webNext(@QueryParam("callback") String callback) {
        if(bot.songList.size() == 0) {
            return callback + "({\"status\":\"failure\"})";
        }
        SongEntry song = bot.songList.get(0);

        JSONObject resp = new JSONObject();
        resp.put("status", "success");
        resp.put("noNewSong", "false");


        JSONObject nextSongObj = new JSONObject();
        nextSongObj.put("vid", song.getVideoId());
        nextSongObj.put("url", song.generateYoutubeUrl());
        nextSongObj.put("id", song.getRequestId());
        nextSongObj.put("user", song.getUser());
        nextSongObj.put("type", "youtube");
        nextSongObj.put("title", song.getTitle());

        resp.put("next", nextSongObj);

        bot.songList.remove(0);
        bot.doNewSong(song);
        return callback + "(" + resp.toString() + ")";
    }


}
