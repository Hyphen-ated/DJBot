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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//based on rakxer's proof of concept, thanks rakxer!
public class BandcampFetcher  {
    public Logger logger = LoggerFactory.getLogger("hyphenated.djbot");
    
    private Pattern relevantDataPattern = Pattern.compile("var SiteData(.+?)var CurrencyData", Pattern.DOTALL);
         
    //keyword in quotes, optional whitespace around a colon, then any text between the first quote and a second quote that is not preceded by a backslash
    private Pattern titlePattern = Pattern.compile("\"title\"\\s*:\\s*\"(.+?)(?<!\\\\)\"");
    private Pattern mp3Pattern = Pattern.compile("\"mp3-128\"\\s*:\\s*\"//([^\"]*)\"");
    private Pattern durationPattern = Pattern.compile("\"duration\"\\s*:\\s*(\\d+)");

    //start of BandData object, then characters that dont close the object, then the name field
    private Pattern bandNamePattern = Pattern.compile("var BandData[^}]*name:\\s*\"(.+?)(?<!\\\\)\"");
    
    public FetchResult fetchSongData(String url) {
        try {
            List<SongEntry> songs = new ArrayList<>();
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
                    return new FetchResult("I couldn't parse bandcamp's song title correctly");
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
                        
            int mp3idx;
            m = mp3Pattern.matcher(relevantData);
            for (mp3idx = 0; mp3idx < songs.size(); ++mp3idx) {
                if(!m.find()) {
                    //we found fewer mp3 links than we did "title" links. this is maybe okay, sometimes there are
                    //other things present with a title after the songs.
                    break;
                }
                if(mp3idx + 1 > songs.size()) {
                    logger.warn("We found more mp3 links than 'title' links on this bandcamp page. aborting.");
                    return new FetchResult("I couldn't understand that bandcamp page");
                }
                songs.get(mp3idx).setVideoId(m.group(1));
                
            }
            //let's just cross our fingers and hope that the earlier titles we found are actually songs, and the
            //later ones are non-song objects to ignore.
            songs = songs.subList(0, mp3idx);
            
            int durationIdx;
            m = durationPattern.matcher(relevantData);
            for (durationIdx = 0; durationIdx < songs.size(); ++durationIdx) {
                if(!m.find()) {
                    logger.warn("We found fewer durations than mp3 links on this bandcamp page. aborting.");
                    return new FetchResult("I couldn't understand that bandcamp page");
                }
                try {
                    if(durationIdx + 1 > songs.size()) {
                        //we found more durations than mp3 links. well, let's ignore the extras and hope that the
                        //first ones we found were the song ones.
                        break;
                    }
                    int duration = Integer.parseInt(m.group(1));
                    songs.get(durationIdx).setDurationSeconds(duration);
                } catch (NumberFormatException e) {
                    return new FetchResult("I couldn't figure out the length of that song");
                }
            }
            
            return new FetchResult(songs);
        } catch (Exception e) {
            logger.error("Error during bandcamp request", e);
            return new FetchResult("I had an error while trying to get that song");
        }
    }
}
