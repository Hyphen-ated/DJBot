package hyphenated.djbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hyphenated.djbot.json.CheckResponse;
import hyphenated.djbot.json.NextResponse;
import hyphenated.djbot.json.SongEntry;
import org.apache.commons.lang.StringUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Path("djbot")
public class DjResource {

    private DjService dj;

    public DjResource(DjConfiguration conf) {
        DjIrcBot irc = new DjIrcBot(conf);
        dj = new DjService(conf, irc);
        irc.setDjService(dj);
        irc.startup();

        //kick off by starting a song
        dj.nextSong();
    }

    public DjService getDj() {
        return dj;
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
        return wrapForJsonp(new CheckResponse(dj.getVolume(), dj.getCurrentSong()), callback);
    }

    @GET
    @Path("updatevolume")
    @Produces("application/json")
    public String webVolume(@QueryParam("callback") String callback, @QueryParam("volume") String volume) {
        dj.setVolume(volume);
        return wrapForJsonp("", callback);
    }

    @GET
    @Path("current")
    @Produces("application/json")
    public String webCurrent(@QueryParam("callback") String callback) {
        dj.startCurrentSong();
        return wrapForJsonp(dj.getCurrentSong(), callback);
    }

    @GET
    @Path("next")
    @Produces("application/json")
    public String webNext(@QueryParam("callback") String callback, @QueryParam("currentId") String currentId) {
        SongEntry song;
        synchronized (dj) {
            if(!StringUtils.isEmpty(currentId) && dj.getCurrentSong() != null) {
                if(! (currentId.equals(String.valueOf(dj.getCurrentSong().getRequestId())))) {
                    //we're trying to skip something that already ended or got skipped
                    dj.logger.info("Trying to skip id " + currentId + " but id " + dj.getCurrentSong().getRequestId() + " is currently playing, so 'next' fails");

                    return wrapForJsonp(new NextResponse("failure"), callback);
                }
            }
            song = dj.nextSong();
        }


        if(song == null) {
            dj.logger.debug("Bot returned a null next song, so 'next' fails");
            return wrapForJsonp(new NextResponse("failure"), callback);
        }

        dj.logger.info("'next'ing to new song with id: " + song.getRequestId());
        return wrapForJsonp(new NextResponse("success", song), callback);
    }

    @GET
    @Path("info")
    @Produces("application/json")
    public String webInfo(@QueryParam("callback") String callback) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return wrapForJsonp(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dj.getStateRepresentation()),
                            callback);

    }


}
