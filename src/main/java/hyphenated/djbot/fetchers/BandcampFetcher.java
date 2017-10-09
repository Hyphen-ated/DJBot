package hyphenated.djbot.fetchers;

import hyphenated.djbot.SiteIds;
import hyphenated.djbot.json.SongEntry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//based on rakxer's proof of concept, thanks rakxer!
public class BandcampFetcher  {
    public Logger logger = LoggerFactory.getLogger("hyphenated.djbot");

    //used to get the contents of script tags in the html page, which we will then parse using the "rhino" library
    private Pattern scriptPattern = Pattern.compile("<script.*?>(.*?)</script>", Pattern.DOTALL);
    
    public FetchResult fetchSongData(String url) {
        try {
            List<SongEntry> songs = new ArrayList<>();
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(url);
            int errcode = client.executeMethod(get);
            if (errcode != 200) {
                return new FetchResult("I couldn't even load that bandcamp page");
            }
    
            String html = IOUtils.toString(get.getResponseBodyAsStream(), "utf-8");
            
            Matcher m = scriptPattern.matcher(html);
            String js = "";
            while(m.find()) {
                js = m.group(1);
                //There are several script tags on the page. the one we care about is the one with this value in it.
                //This could potentially give us the wrong script if this string appears as data somewhere, but
                //that's sufficiently unlikely that I'm going to not worry about it.
                if (js.contains("var BandData = {")) {
                    break;
                }
            }
            
            if (StringUtils.isBlank(js)) {
                return new FetchResult("I couldn't find the song data in that page");
            }

            //take the js string, use rhino to parse it to an Abstract Syntax Tree
            AstRoot root = new Parser().parse(js, "", 1);
            
            //here we walk the entire parsed javascript AST so we can reliably find the "trackinfo" element and band name
            BandcampDataWalker finder = new BandcampDataWalker();
            try {
                root.visit(finder);
            } catch (Exception e) {
                logger.warn("Error while parsing bandcamp js", e);
                return new FetchResult("I had an error while trying to understand that page");
            }
            
            if (finder.trackinfo == null) {
                return new FetchResult("I couldn't find the track info on that page");
            }
            List<AstNode> elems = finder.trackinfo.getElements();
            String bandname = finder.bandname;
            if(StringUtils.isBlank(bandname)) {
                bandname = null; //this is okay, we'll just ignore the bandname if it's null
            }
            //now we have a list of trackinfo objects. each one represents a song.
            for (AstNode node : elems) {
                if(!(node instanceof ObjectLiteral)) {
                    continue; 
                }
                ObjectLiteral literal = (ObjectLiteral) node;
                //delve into that object to find the properties we need to know about a song
                SongEntry song = getSongFromNode(literal, bandname);
                if(song != null) {
                    songs.add(song);
                }
            }
            
            if(songs.size() == 0) {
                return new FetchResult("I couldn't find any songs on that page");
            }
            
            return new FetchResult(songs);
            
        } catch (Exception e) {
            logger.error("Error during bandcamp request", e);
            return new FetchResult("I had an error while trying to get that song");
        }
    }

    
    //given a javascript object literal (which normally contains 30+ key-values for various bandcamp metadata),
    //we go in there to find the songname, the url to the mp3, and the duration.
    //(we prepend the bandname onto the front of the songname)
    //returns null if we can't find all the song info we expected to find
    @Nullable
    private SongEntry getSongFromNode(ObjectLiteral obj, String bandName) {
        String songName = "";
        String mp3Url = "";
        int durationSeconds = 0;
        
        //step over all the properties, if we find one we care about, write it to the variables above. then we check
        //afterwards that we got all the ones we need.
        for(ObjectProperty prop :obj.getElements()) {
            AstNode left = prop.getLeft();
            AstNode right = prop.getRight();
            if(!(left instanceof StringLiteral)) {
                continue;
            }
            String propName = ((StringLiteral) left).getValue();
            //we care about a few specific property names, so we ignore the rest
            switch(propName) {
                case "title":
                    if(right instanceof StringLiteral) {
                        songName = ((StringLiteral)right).getValue();
                        if(bandName != null) {
                            songName = bandName + " - " + songName;
                        }
                    }
                    break;
                case "duration":
                    if(right instanceof NumberLiteral) {
                        double dur = Double.parseDouble(((NumberLiteral)right).getValue());
                        durationSeconds = (int) Math.ceil(dur);
                    }
                    break;
                case "file":
                    //the structure we're looking for here is:
                    //file":{"mp3-128":"https://blah"}
                    if(right instanceof ObjectLiteral) {
                        List<ObjectProperty> list = ((ObjectLiteral)right).getElements();
                        if(list.size() > 0) {
                            ObjectProperty p = list.get(0);
                            AstNode pLeft = p.getLeft();
                            if(pLeft instanceof StringLiteral && ((StringLiteral)pLeft).getValue().equals("mp3-128")) {
                                AstNode pRight = p.getRight();
                                if(pRight instanceof StringLiteral) {
                                    mp3Url = ((StringLiteral)pRight).getValue();
                                    mp3Url = mp3Url.replaceFirst("^https://", "");
                                    mp3Url = mp3Url.replaceFirst("^http://", "");

                                }
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        if(StringUtils.isBlank(songName) || StringUtils.isBlank(mp3Url) || durationSeconds == 0) {
            logger.warn("Couldn't get all the info for a song out of the bandcamp page");
        } else {
            return new SongEntry(songName, mp3Url, -1, "", new Date().getTime(), durationSeconds, false, 0, SiteIds.BANDCAMP);
        }
        return null;
    }
}

//we pass this object to rhino. it visits every node in the js AST.
//the goal is to retrieve the trackinfo array and the bandname.
class BandcampDataWalker implements NodeVisitor {
    //after the walking is done, the code that needs these values will grab them from here:
    public ArrayLiteral trackinfo = null;
    public String bandname = null;

    public boolean visit(AstNode node) {
        //We return as soon as we figure out that this particular node is not one we are looking for.
        //We always return "true" because that means "yes, visit all the children of this node recursively".
        //There's no way to call off the visiting after we've found our target nodes, so we are going to be visiting
        //every single node in the AST regardless.
        if(!(node instanceof ObjectProperty)) {
            return true;
        }
        ObjectProperty prop = (ObjectProperty)node;
        AstNode left = prop.getLeft();
        if(!(left instanceof Name)) {
            return true;
        }
        String name = ((Name)left).getIdentifier();

        tryTrackInfo(prop, name);
        tryArtist(prop, name);

        return true;
    }
    
    private void tryTrackInfo(ObjectProperty prop, String name) {
        //we're looking for the "trackinfo: ..." property, so we can get that "..." rvalue
        if ("trackinfo".equals(name)) {
            if(prop.getRight() instanceof ArrayLiteral) {
                trackinfo = (ArrayLiteral)(prop.getRight());
            }
        }
    }
    
    private void tryArtist(ObjectProperty prop, String name) {
        if("name".equals(name)) {
            //we're looking for the "name" key that is inside of "BandData" in the right place.
            //so here we move up the AST looking at this node's parents.
            if(!(prop.getParent() instanceof ObjectLiteral)) {
                return;
            }
            ObjectLiteral parent = (ObjectLiteral)(prop.getParent());
            if(!(parent.getParent() instanceof VariableInitializer)) {
                return;
            }
            VariableInitializer parentParent = (VariableInitializer)(parent.getParent());
            if(!(parentParent.getTarget() instanceof Name)) {
                return;
            }
            Name initializerName = (Name)(parentParent.getTarget());
            if("BandData".equals(initializerName.getIdentifier())) {
                AstNode right = prop.getRight();
                if(right instanceof StringLiteral) {
                    bandname = ((StringLiteral)right).getValue();
                }
            }
        }
    }
    
    
}
