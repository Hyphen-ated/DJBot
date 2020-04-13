package hyphenated.djbot.fetchers;

import hyphenated.djbot.DjConfiguration;
import hyphenated.djbot.DjService;
import hyphenated.djbot.SiteIds;
import hyphenated.djbot.json.SongEntry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.joda.time.Period;
import org.joda.time.format.ISOPeriodFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class YoutubeFetcher {
    public Logger logger = LoggerFactory.getLogger("hyphenated.djbot");
    
    public DjConfiguration conf;
    public YoutubeFetcher(DjConfiguration conf) {
        this.conf = conf;
    }
    
    public FetchResult fetchSongData(String youtubeId) {
        try {

            JSONObject obj = getJsonForYoutubeId(youtubeId);

            JSONArray items = obj.getJSONArray("items");
            if(items.length() == 0) {
                return new FetchResult("I couldn't find info about that video on youtube");
            }
            JSONObject item = items.getJSONObject(0);
            JSONObject snippet = item.getJSONObject("snippet");
            JSONObject status = item.getJSONObject("status");
            JSONObject contentDetails = item.getJSONObject("contentDetails");
            String title = snippet.getString("title");
            String durationStr = contentDetails.getString("duration");
            //format is like "PT5M30S" for 5 minutes 30 seconds
            Period p = ISOPeriodFormat.standard().parsePeriod(durationStr);
            int durationSeconds = p.toStandardSeconds().getSeconds();

            if (! countryIsAllowed(contentDetails)) {
                return new FetchResult("that video can't be played in the streamer's country");
            }

            if(!("public".equals(status.getString("privacyStatus")) || "unlisted".equals(status.getString("privacyStatus")))) {
                return new FetchResult("that video is private");
            }

            if(!status.getBoolean("embeddable")) {
                return new FetchResult("that video is not allowed to be embedded");
            }

            if(durationSeconds == 0) {
                return new FetchResult("that video has length 0 (probably it's a live stream)");
            }
            
            SongEntry newSong = new SongEntry(title, youtubeId, -1, null, new Date().getTime(), durationSeconds, false, 0, SiteIds.YOUTUBE);
            return new FetchResult(newSong);
        } catch (Exception e) {
            logger.error("Problem with youtube request \"" + youtubeId + "\"", e);
            return new FetchResult("I had an error while trying to add that song");
        }
    }
    
    public FetchResult youtubeSearch(String sender, String q, DjService dj) {
        try {
            if(conf.getMaxSongsPerUser() > 0 && dj.senderCount(sender) >= conf.getMaxSongsPerUser()) {
                return new FetchResult("you have " + conf.getMaxSongsPerUser() + " songs in the queue already");
            }
            
            String searchUrl = "https://www.googleapis.com/youtube/v3/search?videoEmbeddable=true&part=id&q=" + URLEncoder.encode(q, "UTF-8") + "&type=video&regionCode=" + conf.getUserCountryCode() + "&maxResults=5&key=" + conf.getYoutubeAccessToken();
            GetMethod get;
            HttpClient client = new HttpClient();

            get = new GetMethod(searchUrl);
            int errcode = client.executeMethod(get);
            if (errcode != 200) {
                logger.info("Song search error: got code " + errcode + " from " + searchUrl);
                return new FetchResult("I couldn't run that search properly on youtube");
            }

            String resp = IOUtils.toString(get.getResponseBodyAsStream(), "utf-8");
            if (resp == null) {
                logger.info("Couldn't get detail at " + searchUrl);
                return new FetchResult("I couldn't run that search properly on youtube");
            }
            JSONObject searchObj = new JSONObject(resp);

            JSONArray searchItems = searchObj.getJSONArray("items");
            if (searchItems.length() == 0) {
                logger.info("Empty 'items' array in youtube response for url " + searchUrl);
                return new FetchResult("that search gave no results");
            }

            for (int i = 0; i < searchItems.length(); ++i) {
                JSONObject resultItem = searchItems.getJSONObject(i);
                JSONObject id = resultItem.getJSONObject("id");
                String videoId = id.getString("videoId");
                JSONObject obj = getJsonForYoutubeId(videoId);

                JSONArray items = obj.getJSONArray("items");
                if (items.length() == 0) {
                    continue;
                }
                JSONObject item = items.getJSONObject(0);
                JSONObject snippet = item.getJSONObject("snippet");
                JSONObject status = item.getJSONObject("status");
                JSONObject contentDetails = item.getJSONObject("contentDetails");
                String title = snippet.getString("title");
                String durationStr = contentDetails.getString("duration");
                //format is like "PT5M30S" for 5 minutes 30 seconds
                Period p = ISOPeriodFormat.standard().parsePeriod(durationStr);
                int durationSeconds = p.toStandardSeconds().getSeconds();


                if (!countryIsAllowed(contentDetails)) {
                    continue;
                }

                if (!("public".equals(status.getString("privacyStatus")) || "unlisted".equals(status.getString("privacyStatus")))) {
                    continue;
                }

                if (!status.getBoolean("embeddable")) {
                    continue;
                }

                if (durationSeconds == 0) {
                    continue;
                }

                SongEntry newSong = new SongEntry(title, videoId, -1, sender, new Date().getTime(), durationSeconds, false, 0, SiteIds.YOUTUBE);

                if (dj.getPossiblePolicyFailureReason(newSong, sender) != null) {
                    continue;
                }
                
                return new FetchResult(newSong);
            }

            //if we get here, it means none of the songs in the results were okay
            return new FetchResult("I didn't find an appropriate video early enough in the search results");
        } catch (Exception e) {
            logger.error("Problem with youtube search \"" + q + "\"", e);
            return new FetchResult("I had an error while trying to add that song");
        }
    }

    private JSONObject getJsonForYoutubeId(String youtubeId) throws Exception {
        String infoUrl = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails,snippet,status&id=" + youtubeId + "&key=" + conf.getYoutubeAccessToken();
        GetMethod get;
        HttpClient client = new HttpClient();
        get = new GetMethod(infoUrl);
        int errcode = client.executeMethod(get);
        if(errcode != 200) {
            throw new RuntimeException("Http error " + errcode + " from youtube for id " + youtubeId);
        }

        String resp = IOUtils.toString(get.getResponseBodyAsStream(), "utf-8");
        if(resp == null) {
            logger.info("Couldn't get detail at " + infoUrl);
            throw new RuntimeException("Couldn't understand youtube's response for id "  + youtubeId);
        }
        return new JSONObject(resp);
    }

    //youtube enforces country restrictions.
    //we want to know about it beforehand to fail the song when requested instead of when played.
    private boolean countryIsAllowed(JSONObject contentDetails) {
        JSONObject regionRestriction = contentDetails.optJSONObject("regionRestriction");
        if(regionRestriction != null) {
            //if there's an "allowed" list and we're not in it, fail.
            //if there's a "blocked" list and we are in it, fail
            JSONArray allowed =  regionRestriction.optJSONArray("allowed");
            if(allowed != null) {
                boolean weAreAllowed = false;
                for(int i = 0; i < allowed.length(); ++i) {
                    String country = allowed.getString(i);
                    if(country.equalsIgnoreCase(conf.getUserCountryCode())) {
                        weAreAllowed = true;
                    }
                }
                if(!weAreAllowed) {
                    return false;
                }
            }

            JSONArray blocked = regionRestriction.optJSONArray("blocked");
            if(blocked != null) {
                for(int i = 0; i < blocked.length(); ++i) {
                    String country = blocked.getString(i);
                    if(country.equalsIgnoreCase(conf.getUserCountryCode())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    //we can only get here through the streamer's request
    public FetchResult fetchSongListData(String listPathId) {

        String infoUrl = " https://www.googleapis.com/youtube/v3/playlistItems?part=contentDetails&playlistId=" + listPathId + "&key=" + conf.getYoutubeAccessToken() + "&maxResults=50";

        GetMethod get = new GetMethod(infoUrl);
        HttpClient client = new HttpClient();
        try {
            int errcode = client.executeMethod(get);
            if(errcode != 200) {
                logger.info("Song request error: got code " + errcode + " from " + infoUrl);
                return new FetchResult("I couldn't find info about that playlist on youtube");
            }
        } catch (IOException e) {
            logger.warn("Couldn't get info from youtube api", e);
            return new FetchResult( "I couldn't find info about that playlist on youtube");
        }

        try {
            String resp = IOUtils.toString(get.getResponseBodyAsStream(), "utf-8");
            if(resp == null) {
                logger.info("Couldn't get detail at " + infoUrl);
                return new FetchResult( "I couldn't find info about that playlist on youtube");
            }
            JSONObject obj = new JSONObject(resp);
            JSONArray items = obj.getJSONArray("items");
            List<SongEntry> songs = new ArrayList<>(50);
            int skipCount = 0;
            for(int i = 0; i < items.length(); ++i) {
                JSONObject item = items.getJSONObject(i);
                JSONObject contentDetails = item.getJSONObject("contentDetails");
                String videoId = contentDetails.getString("videoId");
                
                FetchResult result = this.fetchSongData(videoId);
                if(result.songs != null && result.songs.size() > 0) {
                    SongEntry song = result.songs.get(0);
                    song.setBackup(true);
                    songs.add(song);
                } else {
                    ++skipCount;
                }
            }
            return new FetchResult(songs, skipCount);

        } catch (Exception e) {
            logger.error("Problem with youtube playlist request \"" + listPathId + "\"", e);
            return new FetchResult("I had an error while trying to add that list");
        }

    }
}
