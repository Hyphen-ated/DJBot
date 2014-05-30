package hyphenated.djbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hyphenated.djbot.json.CheckResponse;
import hyphenated.djbot.json.NextResponse;
import hyphenated.djbot.json.SongEntry;
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

    private String wrapForJsonp(Object response, String callback) {
        String objString = null;
        if(response instanceof String) {
            objString = (String) response;
        } else {
            ObjectMapper mapper = new ObjectMapper();

            try {
                objString = mapper.writeValueAsString(response);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Object of type " + response.getClass().getName() + " not json serializable");
            }
        }
        if(callback != null) {
            return callback + "(" + objString + ")";
        } else {
            return objString;
        }
    }

    @GET
    @Path("check")
    @Produces("application/json")
    public String webCheck(@QueryParam("callback") String callback) {
        return wrapForJsonp(new CheckResponse(bot.getVolume(), bot.getCurrentSong()), callback);
    }

    @GET
    @Path("updatevolume")
    @Produces("application/json")
    public String webVolume(@QueryParam("callback") String callback, @QueryParam("volume") String volume) {
        bot.setVolume(volume);
        return wrapForJsonp("", callback);
    }

    @GET
    @Path("current")
    @Produces("application/json")
    public String webCurrent(@QueryParam("callback") String callback) {
        bot.startCurrentSong();
        return wrapForJsonp(bot.getCurrentSong(), callback);
    }

    @GET
    @Path("next")
    @Produces("application/json")
    public String webNext(@QueryParam("callback") String callback, @QueryParam("idToSkip") String idToSkip) {
        SongEntry song;
        synchronized (bot) {
            if(bot.noMoreSongs()) {
                return wrapForJsonp(new NextResponse("failure"), callback);
            }

            if(!StringUtils.isEmpty(idToSkip)) {
                if(! (idToSkip.equals(bot.getCurrentSong().getRequestId()))) {
                    //we're trying to skip something that already ended or got skipped
                    return wrapForJsonp(new NextResponse("failure"), callback);
                }
            }
            song = bot.nextSong();
        }


        if(song == null) {
            return wrapForJsonp(new NextResponse("failure"), callback);
        }

        return wrapForJsonp(new NextResponse("success", song), callback);
    }

    @GET
    @Path("info")
    @Produces("application/json")
    public String webInfo(@QueryParam("callback") String callback) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return wrapForJsonp(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bot.getStateRepresentation()),
                            callback);

    }


}
