package hyphenated.djbot.fetchers;

import hyphenated.djbot.SiteIds;
import hyphenated.djbot.json.SongEntry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//based on rakxer's proof of concept, thanks rakxer!
public class BandcampFetcher  {
    public Logger logger = LoggerFactory.getLogger("hyphenated.djbot");
    
    private Pattern relevantDataPattern = Pattern.compile("var SiteData(.+?)var CurrencyData", Pattern.DOTALL);
         
    //keyword in quotes, optional whitespace around a colon, then any text between the first quote and a second quote that is not preceded by a backslash
    private Pattern titlePattern = Pattern.compile("\"title\"\\s*:\\s*\"(.+?)(?<!\\\\)\"");
    private Pattern mp3Pattern = Pattern.compile("\"mp3-128\"\\s*:\\s*\"//([^\"]*\")");
    private Pattern durationPattern = Pattern.compile("\"duration\"\\s*:\\s*(\\d+)");

    //start of BandData object, then characters that dont close the object, then the name field
    private Pattern bandNamePattern = Pattern.compile("var BandData[^}]*name:\\s*\"(.+?)(?<!\\\\)\"");
    
    public FetchResult fetchSongData(String url) {
        try {
            ArrayList<SongEntry> songs = new ArrayList<>();
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(url);
            int errcode = client.executeMethod(get);
            if (errcode != 200) {
                return new FetchResult("I couldn't get info about that from bandcamp");
            }
    
            String html = IOUtils.toString(get.getResponseBodyAsStream(), "utf-8");
            Matcher m = relevantDataPattern.matcher(html);
            
            if (!m.find()) {
                return new FetchResult("I couldn't understand bandcamp's song data");
            }
            String relevantData = m.group(1); //reduces the size of the html string to speed up future regex
            
            m = bandNamePattern.matcher(relevantData);

            String bandName = null;
            if(m.find()) {
                bandName = m.group(1);
            } else {
                logger.warn("band name not found on bandcamp page " + url);
            }
            
            m = titlePattern.matcher(relevantData);
            //must discard the first result because that's the "current" track, which doesn't have all info and the info that it does have is duped
            m.find();
            while (m.find()) {
                String title = m.group(1);
                if(title == null) {
                    return new FetchResult("couldn't parse bandcamp's song title correctly");
                }
                if(bandName != null) {
                    title = bandName + " - " + title;
                }
                
                SongEntry song = new SongEntry(title, "", -1, "", new Date().getTime(), -1, false, 0, SiteIds.BANDCAMP);
                songs.add(song);
            }
            
            if(songs.size() == 0) {
                return new FetchResult("I couldn't find the song on that page");
            }
                        
            m = mp3Pattern.matcher(relevantData);
            for (int i = 0; i < songs.size(); ++i) {
                if(!m.find()) {
                    return new FetchResult("couldn't understand that bandcamp page");
                }
                songs.get(i).setVideoId(m.group(1));
            }
            
            m = durationPattern.matcher(relevantData);
            for (int i = 0; i < songs.size(); ++i) {
                if(!m.find()) {
                    return new FetchResult("couldn't understand that bandcamp page");
                }
                try {
                    int duration = Integer.parseInt(m.group(1));
                    songs.get(i).setDurationSeconds(duration);
                } catch (NumberFormatException e) {
                    return new FetchResult("couldn't figure out the length of that song");
                }
            }
            
            return new FetchResult(songs);
        } catch (Exception e) {
            logger.error("Error during bandcamp request", e);
            return new FetchResult("I had an error while trying to get that song");
        }
    }
}
