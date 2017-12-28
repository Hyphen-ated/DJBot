package hyphenated.djbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hyphenated.djbot.db.SongQueueDAO;
import hyphenated.djbot.json.LoginInfo;
import hyphenated.djbot.auth.User;
import hyphenated.djbot.json.CheckResponse;
import hyphenated.djbot.json.NextResponse;
import hyphenated.djbot.json.SongEntry;
import io.dropwizard.auth.Auth;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("djbot")
public class DjResource {

    private DjService dj;
    private boolean publicMode;
    //use user tokens on auth-required endpoints to prevent csrf attack
    private Map<String, User> tokenMap = new HashMap<>();

    public DjResource(DjConfiguration conf, HttpClient httpClient, SongQueueDAO dao) {
        publicMode = conf.isDjbotPublic();

        String host = conf.getTwitchIrcHost();
        if (StringUtils.isBlank(host)) {
            try {
                host = getIrcHostFromTwitch(conf, httpClient);
            } catch (Exception e) {
                throw new RuntimeException("Unable to get irc server host from twitch's api. Try setting 'twitchIrcHost' in options.yaml.", e);
            }
        }

        DjIrcBot irc = new DjIrcBot(conf, host);
        dj = new DjService(conf, irc, dao);
        irc.setDjService(dj);
        irc.startup();

        //kick off by starting a song
        dj.nextSong();
    }



    public DjService getDj() {
        return dj;
    }

    private String getIrcHostFromTwitch(DjConfiguration conf, HttpClient httpClient) throws Exception {
        String twitchServerInfoUrl = conf.getTwitchChatServerAssignmentUrl().replace("%CHANNEL%", conf.getChannel());
        HttpGet httpGet = new HttpGet(twitchServerInfoUrl);
        InputStream resultStream = httpClient.execute(httpGet).getEntity().getContent();
        String result = IOUtils.toString(resultStream);
        JSONObject json = new JSONObject(result);
        JSONArray servers = json.getJSONArray("servers");
        return servers.getString(0);
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
        if(response instanceof String) {
            objString = '\"' + objString + '\"';
        }
        if(callback != null) {
            return callback + "(" + objString + ")";
        } else {
            return objString;
        }
    }

    private void removeTokenEntry(User user) {
        String tokenToRemove = null;
        for (Map.Entry<String, User> entry : tokenMap.entrySet()) {
            if(entry.getValue().getName().equals(user.getName())) {
                tokenToRemove = entry.getKey();
                break;
            }
        }
        if(tokenToRemove != null) {
            tokenMap.remove(tokenToRemove);
        }

    }

    @GET
    @Path("/authenabled")
    @Produces("application/json")
    public String webAuthEnabled(@QueryParam("callback") String callback) {
        return wrapForJsonp(publicMode, callback);
    }

    @GET
    @Path("/streamname")
    @Produces("application/json")
    public String webStreamName(@QueryParam("callback") String callback) {
        return wrapForJsonp(dj.getStreamer(), callback);
    }

    @GET
    @Path("/login")
    @Produces("application/json")
    public String webLogin(@Auth User user, @QueryParam("callback") String callback) {
        if(user != null) {
            removeTokenEntry(user);
            String userToken = UUID.randomUUID().toString();
            tokenMap.put(userToken, user);
            return wrapForJsonp(new LoginInfo(user.getName(), userToken), callback);
        } else {
            return wrapForJsonp(new LoginInfo(null, null), callback);
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
    public String webVolume(@QueryParam("callback") String callback, @QueryParam("volume") String volume, @QueryParam("userToken") String userToken) {
        validateUserToken(userToken);
        dj.setVolume(volume);
        return wrapForJsonp("", callback);
    }

    private void validateUserToken(String userToken) {
        if(publicMode) {
            User user = null;
            if(userToken != null) {
                user = tokenMap.get(userToken);
            }
            if(user == null || !user.isAdmin()) {
                throw new BadRequestException("bad user token");
            }
        }
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
    public String webNext(@QueryParam("callback") String callback, @QueryParam("currentId") String currentId, @QueryParam("userToken") String userToken) {
        validateUserToken(userToken);
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
    public String webInfo(@QueryParam("callback") String callback, @QueryParam("userToken") String userToken) throws Exception {
        validateUserToken(userToken);
        ObjectMapper mapper = new ObjectMapper();
        return wrapForJsonp(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dj.getStateRepresentation()),
                            callback);

    }

    @GET
    @Path("like")
    @Produces("application/json")
    public String webLike(@QueryParam("callback") String callback, @QueryParam("userToken") String userToken) throws Exception {
        validateUserToken(userToken);
        dj.likeSong();
        return wrapForJsonp(true, callback);

    }

    @GET
    @Path("songlist")
    @Produces("application/json")
    public String webSonglist(@QueryParam("callback") String callback) throws Exception {
        return wrapForJsonp(dj.getSonglist(), callback);
    }

}
