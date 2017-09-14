package hyphenated.djbot.fetchers;

import hyphenated.djbot.SiteIds;
import hyphenated.djbot.json.SongEntry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Date;

public class SoundcloudFetcher {
    public Logger logger = LoggerFactory.getLogger("hyphenated.djbot");

    public FetchResult fetchSongData(String songURL) {
        try {

            JSONObject attributes = getJsonFromSoundcloud(songURL);
            if(attributes == null) {
                return new FetchResult("I couldn't get info about that song from soundcloud");
            }

            String kind = attributes.optString("kind");

            if(!kind.equals("track")) {
                return new FetchResult("soundcloud says that isn't a song");
            }

            String soundcloudId = attributes.getString("permalink_url").replaceFirst("https://soundcloud.com", "");

            int durationMillis = attributes.optInt("duration", 0);
            int durationSeconds = durationMillis / 1000;
            String title = attributes.optString("title", "<title not found>");

            SongEntry newSong = new SongEntry(title, soundcloudId, -1, null, new Date().getTime(), durationSeconds, false, 0, SiteIds.SOUNDCLOUD);
            
            return new FetchResult(newSong);
        } catch (Exception e) {
            logger.error("Problem with soundcloud request \"" + songURL + "\"", e);
            return new FetchResult("I had an error while trying to add that song");
        }
    }

    @Nullable
    private JSONObject getJsonFromSoundcloud(String songURL) throws Exception {
        //rakxer applied for an api account and they granted it after a 1 month wait. this is his client_id. we don't
        //need a client secret for anything, since all we're trying to do is resolve public information about songs.
        String infoUrl = "https://api.soundcloud.com/resolve.json?client_id=LOjEEQE0Y1J2hFb08g7IYmj0D3oYiRiH&url=" + songURL ;

        GetMethod get;
        HttpClient client = new HttpClient();
        get = new GetMethod(infoUrl);
        int errcode = client.executeMethod(get);
        if(errcode != 200) {
            throw new RuntimeException("Http error " + errcode + " from soundcloud");
        }
        String resp = IOUtils.toString(get.getResponseBodyAsStream(), "utf-8");
        if(resp == null) {
            logger.info("Couldn't get detail at " + infoUrl);
            throw new RuntimeException("Couldn't understand soundcloud's response for id "  + songURL);
        }

        return new JSONObject(resp);
    }
}
