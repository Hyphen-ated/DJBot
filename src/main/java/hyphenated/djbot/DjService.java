package hyphenated.djbot;

import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.sharing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import hyphenated.djbot.db.SongQueueDAO;
import hyphenated.djbot.fetchers.*;
import hyphenated.djbot.json.SongEntry;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.ISOPeriodFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//the main brains of the djbot, handles the queues and whatnot
public class DjService {
    public Logger logger = LoggerFactory.getLogger("hyphenated.djbot");

    private final String nowPlayingFilePath = "nowPlayingInfo.txt";
    private final String dboxFilePath = "/songlist.txt";

    private final DjConfiguration conf;
    private final DjIrcBot irc;
    private final SongQueueDAO dao;

    private volatile SongEntry currentSong;
    private volatile ArrayList<SongEntry> songList = new ArrayList<>();
    private volatile ArrayList<SongEntry> secondarySongList = new ArrayList<>();
    private volatile ArrayList<SongEntry> songHistory = new ArrayList<>();

    private volatile ArrayList<SongEntry> lastPlayedSongs = new ArrayList<>();

    //given a user, what is the ID of the last song they requested? this is used for !wrongsong
    public volatile HashMap<String, Integer> lastRequestIdByUser = new HashMap<>();

    private volatile int volume;
    private volatile int nextRequestId;

    //the following things should not change after initialization
    private final String streamer;
    private final String dropboxLink;
    private Set<String> blacklistedYoutubeIds; //immutable after creation

    private YoutubeFetcher ytFetcher;
    private SoundcloudFetcher scFetcher;

    public DjService(DjConfiguration newConf, DjIrcBot irc, SongQueueDAO dao) {
        this.conf = newConf;
        this.streamer = conf.getChannel();
        this.volume = conf.getDefaultVolume();
        this.irc = irc;
        this.dao = dao;
        this.ytFetcher = new YoutubeFetcher(conf);
        this.scFetcher = new SoundcloudFetcher();

        moveLegacySongsJsonToDb();

        long historyCutoff = DateTime.now().minusWeeks(2).toDate().getTime();
        songHistory = new ArrayList(dao.getSongsAfterDate(historyCutoff));
        secondarySongList = new ArrayList(dao.getSongsToPlay());

        this.nextRequestId = dao.getHighestId() + 1;

        dropboxLink = determineDropboxLink(conf);

        HashSet<String> blacklist = new HashSet<>();
        if(conf.getBlacklistedYoutubeIds() != null) {
            blacklist.addAll(conf.getBlacklistedYoutubeIds());
        }
        this.blacklistedYoutubeIds = Collections.unmodifiableSet(blacklist);

        //create the scrollable console gui window
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GuiWindow.createAndShowGUI(conf.getMaxConsoleLines());
            }
        });

    }

    //djbot used to use these two files to hold queue data. now it uses sqlite.
    //this is a migration to go from the files to the db.
    private final String queueHistoryFilePath = "queue.json";
    private final String unplayedSongsFilePath = "unplayedSongs.json";
    private void moveLegacySongsJsonToDb() {
        List<String> history = null;
        File queueHistoryFile = new File(queueHistoryFilePath);
        File unplayedSongsFile = new File(unplayedSongsFilePath);

        if(!(queueHistoryFile.exists()))
            return;

        if(!(unplayedSongsFile.exists()))
            return;

        logger.info("Found old-style song queue files at '" + queueHistoryFilePath + "' and '" + unplayedSongsFilePath +
                "'. We're adding them to a database and then renaming the files to end in .old");

        try {
            history = IOUtils.readLines(new FileInputStream(queueHistoryFilePath), "utf-8");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't find file at " + queueHistoryFilePath, e);
        }

        String unplayedSongsStr = null;
        try {
            unplayedSongsStr = IOUtils.toString(new FileInputStream(unplayedSongsFilePath), "utf-8");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't find file at " + unplayedSongsFilePath, e);
        }

        ObjectMapper mapper = new ObjectMapper();
        ArrayList<Integer> unplayedSongsList = null;
        try {
            unplayedSongsList = mapper.readValue(unplayedSongsStr, ArrayList.class);
        } catch (IOException e) {
            throw new RuntimeException("File at " + unplayedSongsFilePath + " probably has invalid format (it should be json with just an array of integers)", e);
        }
        HashSet<Integer> unplayedSongs = new HashSet<>();
        unplayedSongs.addAll(unplayedSongsList);

        int lineNumber = 1;
        ArrayList<SongEntry> entries = new ArrayList<>();
        for(String historyElement : history) {
            SongEntry entry = null;
            try {
                entry = mapper.readValue(historyElement, SongEntry.class);
            } catch (IOException e) {
                throw new RuntimeException("File at " + queueHistoryFilePath + " has a problem at line " + lineNumber + ": the line won't parse to a json object with the right format.", e);
            }

            entries.add(entry);
        }
        dao.addSongs(entries.iterator());

        for(int id : unplayedSongs) {
            dao.setSongToBePlayed(id, true);
        }
        try {
            FileUtils.moveFile(queueHistoryFile, new File(queueHistoryFilePath + ".old"));
            FileUtils.moveFile(unplayedSongsFile, new File(unplayedSongsFilePath + ".old"));
        } catch (Exception e) {
            logger.error("Ran into an error while trying to rename the now-obsolete queue files", e);
        }
    }

    private String determineDropboxLink(DjConfiguration conf) {
        DbxClientV2 client = getDbxClient();
        try {
            String url;

            DbxUserSharingRequests share = client.sharing();

            ListSharedLinksResult result = share.listSharedLinksBuilder().withPath(dboxFilePath).start();
            List<SharedLinkMetadata> links = result.getLinks();
            if(links.size() > 0) {
                url = links.get(0).getUrl();
            } else {
                SharedLinkSettings settings = new SharedLinkSettings(RequestedVisibility.PUBLIC, null, null);
                SharedLinkMetadata metadata = share.createSharedLinkWithSettings(dboxFilePath, settings);
                url = metadata.getUrl();
            }

            return url.replace("?dl=0", "?raw=1");
        } catch (DbxException e) {
            logger.error("Can't create dropbox link", e);
            return null;
        }
    }

    private DbxClientV2 getDbxClient() {
        DbxRequestConfig config = new DbxRequestConfig("djbot/1.0", Locale.getDefault().toString());
        return new DbxClientV2(config, conf.getDropboxAccessToken());
    }

    public synchronized void irc_songlist( String sender) {
        if(StringUtils.isEmpty(dropboxLink)) {
            irc.message("Sorry " + sender + ", songlist isn't set up");
        } else {
            irc.message(sender + ": see the song list at " + dropboxLink);
        }
    }

    public synchronized void irc_removesong( String sender, String skipIdStr) {
        int skipId;
        try {
            skipId = Integer.parseInt(skipIdStr);
        } catch (NumberFormatException e) {
            irc.message(sender + ": you must specify the song id to remove or skip (a number)");
            return;
        }

        boolean isMod = irc.isMod(sender);

        if (currentSong.getRequestId() == skipId && isMod) {
            nextSong();
        }

        removeSongFromList(songList, skipId, sender, isMod);
        removeSongFromList(secondarySongList, skipId, sender, isMod);

        try {
            dao.setSongToBePlayed(skipId, false);
            updateSongList();
        } catch (IOException e) {
            logger.error("problem updating playedSongs", e);
        }


    }

    private void removeSongFromList(List<SongEntry> listOfSongs, int skipId, String sender, boolean isMod) {
        Iterator<SongEntry> entryIterator = listOfSongs.iterator();
        while (entryIterator.hasNext()) {
            SongEntry curEntry = entryIterator.next();
            if (curEntry.getRequestId() == skipId) {
                if(isMod || curEntry.getUser().equals(sender)) {
                    irc.message(sender + ": removed song \"" + curEntry.getTitle() + "\"");
                    entryIterator.remove();
                } else {
                    irc.message(sender + ": to remove others' songs you need to be mod");
                }
            }
        }
    }


    public synchronized void irc_volume(String sender, String newVolStr) {
        if(StringUtils.isEmpty(newVolStr)) {
            irc.message(sender + ": current volume is " + volume);
            return;
        }

        if(!irc.isMod(sender)) {
            irc.message(sender + ": changing volume is for mods only");
            return;
        }

        int oldVolume = volume;
        int newVolume;
        if("up".equalsIgnoreCase(newVolStr)) {
            newVolume = oldVolume + 10;
        } else if ("down".equalsIgnoreCase(newVolStr)) {
            newVolume = oldVolume - 10;
        } else {
            try {
                newVolume = Integer.parseInt(newVolStr);
            } catch (NumberFormatException e) {
                irc.message( sender + ": volume must be 'up', 'down', or a number between 1 and 100");
                return;
            }
        }

        if (newVolume < 1) {
            newVolume = 1;
            if(oldVolume == newVolume) {
                irc.message(sender + ": volume is already at 1, the minimum");
                return;
            }
        } else if (newVolume > 100) {
            newVolume = 100;
            if(oldVolume == newVolume) {
                irc.message(sender + ": volume is already at 100, the maximum");
                return;
            }
        }

        irc.message(sender + ": volume changed from " + oldVolume + " to " + newVolume);
        volume = newVolume;
    }

    public void irc_songs(String sender) {
        irc.message( sender + ": see " + conf.getHelpUrl() + " for help");
    }


    public synchronized void irc_currentsong(String sender) {
        if(currentSong == null) {
            irc.message( sender + ": no current song (or the server just restarted)");
        } else {
            irc.message( sender + ": Current song: \"" + currentSong.getTitle() + "\", url: " + currentSong.buildSongUrl() + ", id: " + currentSong.getRequestId());
        }
    }

    public synchronized void irc_lastsong(String sender) {
        if(lastPlayedSongs.size() == 0) {
            irc.message(sender + ": no song has finished playing yet");
            return;
        }
        irc.message( sender + ": Last song: \"" + currentSong.getTitle() + "\", url: " + currentSong.buildSongUrl());
    }

    public synchronized void irc_nextsong(String sender) {
        SongEntry nextSong;
        if(songList.size() == 0) {
            if(secondarySongList.size() == 0) {
                irc.message(sender + ": there is no next song");
                return;
            }
            nextSong = secondarySongList.get(0);
        } else {
            nextSong = songList.get(0);
        }

        irc.message(sender + ": Next song: \"" + nextSong.getTitle() + " \", url: " + currentSong.buildSongUrl());
        return;
    }


    Pattern idPattern = Pattern.compile("[a-zA-Z0-9_-]{11}");
    //given a string that a user songrequested, try to figure out what it is a link to and use a fetcher to retrieve
    //metadata about that songs from the appropriate site.
    private FetchResult fetchSongInfo(String sender, String requestStr) {
        String possibleId;
        //we support !songrequest <youtubeid>
        if(requestStr.length() >= 11) {
            possibleId = requestStr.substring(0, 11);
            Matcher m = idPattern.matcher(possibleId);
            
            if (m.matches()) {
                return ytFetcher.fetchSongData(possibleId);
            }
        }

        //we support youtu.be/<youtubeid>
        possibleId = findYoutubeIdAfterMarker(requestStr, "youtu.be/");
        if(possibleId != null) {
            return ytFetcher.fetchSongData(possibleId);
        }

        //we support standard youtube links like https://www.youtube.com/watch?v=<youtubeid>
        possibleId = findYoutubeIdAfterMarker(requestStr, "v=");
        if(possibleId != null) {
            return ytFetcher.fetchSongData(possibleId);
        }

        //we support youtube links like https://www.youtube.com/v/<youtubeid>
        possibleId = findYoutubeIdAfterMarker(requestStr, "/v/");
        if(requestStr.contains("youtube.com") && possibleId != null) {
            return ytFetcher.fetchSongData(possibleId);
        }

        //we support PLAYLISTS like https://www.youtube.com/playlist?list=<playlistid>
        String listPathVar = "?list=";
        int listPathIndex = requestStr.lastIndexOf(listPathVar);
        if(listPathIndex > -1) {
            if(!sender.equals(streamer)) {
                return new FetchResult("Only the streamer can request lists");
            }
            possibleId = requestStr.substring(listPathIndex + listPathVar.length());
            return ytFetcher.fetchSongListData(possibleId);
        }

        //we support soundcloud links like https://soundcloud.com/<creator/songs>
        if (requestStr.startsWith("https://soundcloud.com/")) {
            return scFetcher.fetchSongData(requestStr);
        }
        
        //couldn't figure out what else it could be, so it's a search.
        //searches only go to youtube for now.
        return ytFetcher.youtubeSearch(sender, requestStr, this);
    }
    

    public synchronized void irc_songRequest(String sender, String requestStr) {
        //they didnt actually request anything. show them the help page.
        if(StringUtils.isBlank(requestStr)) {
            irc_songs(sender);
            return;
        }
        
        FetchResult fetched = fetchSongInfo(sender, requestStr);
        enforcePolicyOnRequest(fetched, sender, requestStr);
    }

    public synchronized void irc_soundcloud(String sender, String requestStr) {
        if(StringUtils.isBlank(requestStr)) {
            denySong(sender, "you didn't provide a soundcloud URL (or just the part after soundcloud.com/ )");
            return;
        }

        String url = requestStr;
        if (requestStr.trim().charAt(0) == '/') requestStr = requestStr.replaceFirst("/", "");
        if(! requestStr.startsWith("https://soundcloud.com/")) {
            url = "https://soundcloud.com/" + url;
        }
        FetchResult fetched = scFetcher.fetchSongData(url);
        enforcePolicyOnRequest(fetched, sender, requestStr);
    }

    //returns a string describing why the song isn't allowed, or null if the song is fine
    public String getPossiblePolicyFailureReason(SongEntry song, String sender) {
        if(!sender.equals(streamer) && song.getDurationSeconds() / 60.0 > songLengthAllowedMinutes()) {
            return "the song is over " + songLengthAllowedMinutes() + " minutes";
        }

        if(!sender.equals(streamer) && conf.getQueueSize() > 0 && songList.size() >= conf.getQueueSize()) {
            return "the queue is full at " + conf.getQueueSize();
        }

        if(!sender.equals(streamer) && idCountInMainList(song.getVideoId()) > 0) {
            return "the song \"" + song.getTitle() + "\" is already in the queue";
        }

        if(!sender.equals(streamer) && currentSong != null && song.getVideoId().equals(currentSong.getVideoId())) {
            return "the song \"" + song.getTitle() + "\" is currently playing";
        }

        if(!sender.equals(streamer) && songIsProhibitedByRecentDaysPolicy(song.getVideoId(), song.getTitle())) {
            return "the song \"" + song.getTitle() + "\" has been played in the last " + conf.getRecencyDays() + " days";
        }

        if(!sender.equals(streamer) && conf.getMaxSongsPerUser() > 0 && senderCount(sender) >= conf.getMaxSongsPerUser()) {
            return "you have " + conf.getMaxSongsPerUser() + " songs in the queue already";
        }

        if(!sender.equals(streamer) && moveToPrimaryIfSongInSecondary(song.getVideoId())) {
            return sender + ": bumping \"" + song.getTitle() + "\" to main queue" ;
        }
        return null;
    }
    
    private void enforcePolicyOnRequest(FetchResult fetched, String sender, String requestStr) {
        if(fetched.failureReason != null) {
            denySong(sender, fetched.failureReason);
            return;
        }
        if(fetched.songs.size() == 0) {
            denySong(sender, "something went wrong and I lost track of what song I was looking for");
            return;
        }

        int songsAdded = 0;
        
        if(fetched.songs.size() == 1) {
            SongEntry song = fetched.songs.get(0);
            song.setStartSeconds(extractStartSecondsFromTimeParam(requestStr));
            String failure = getPossiblePolicyFailureReason(song, sender);
            if(failure != null) {
                denySong(sender, failure);
                return;
            }
            songsAdded = addSongToQueue(sender, song);

            //this will cause a redundant looking double message if "up next" messages are enabled and there is no song playing.
            //but that's better than the alternative of missing the message entirely if no songs are playing at all and the player is not open
            //(which is a common scenario when someone first sets up their bot)
            if(songsAdded == 1) {
                irc.message(sender + ": added \"" + song.getTitle() + "\" to queue. id: " + song.getRequestId());
            }
            return;
        }

        //we dont have to enforce policy when there's more than one song, because only the streamer can request lists
        for(SongEntry song : fetched.songs) {
            songsAdded += addSongToQueue(sender, song);
        }
        
        irc.message("Added " + songsAdded + " songs to secondary queue");

    }
    
    //remove the last songs someone added
    public synchronized void irc_wrongsong(String sender) {
        Integer songId = lastRequestIdByUser.get(sender);
        if(songId == null || songId == 0) {
            irc.message(sender + ": I don't have a most-recent request from you I can undo");
        }
        this.removeSongFromList(songList, songId, sender, false);
        this.removeSongFromList(secondarySongList, songId, sender, false);
        lastRequestIdByUser.put(sender, 0);

    }

    public synchronized void irc_songSearch(String sender, String q) {


    }


    private static final Pattern timePattern = Pattern.compile("(((\\d)+)m)?(((\\d)+)s)?");

    //given a request string, look for something like &t=1m22s in there, parse the time value, and return the number of seconds it represents
    private int extractStartSecondsFromTimeParam(String requestStr) {
        int paramIdx = requestStr.lastIndexOf("&t=");
        if(paramIdx < 0) {
            paramIdx = requestStr.lastIndexOf("#t=");
        }
        if(paramIdx < 0) {
            return 0;
        }
        int startOfTimeIdx = paramIdx + 3;
        int endOfTimeIdx;
        for(endOfTimeIdx = startOfTimeIdx; endOfTimeIdx < requestStr.length(); ++endOfTimeIdx) {
            char c = requestStr.charAt(endOfTimeIdx);
            if(c != 'm' && c != 's' && !Character.isDigit(c)) {
                break;
            }
        }

        String timeStr = requestStr.substring(startOfTimeIdx, endOfTimeIdx);
        logger.info("trying to parse " + timeStr + " as a time string");

        Matcher matcher = timePattern.matcher(timeStr);
        if(!matcher.matches()) {
            return 0;
        }
        //minutes and seconds
        String mStr = matcher.group(2);
        String sStr = matcher.group(5);
        int m, s;
        try {
            m = Integer.parseInt(mStr);
        } catch (NumberFormatException e) {
            m = 0;
        }

        try {
            s = Integer.parseInt(sStr);
        } catch (NumberFormatException e) {
            s = 0;
        }

        return m * 60 + s;
    }

    //given a request string, look for a youtube id that directly follows the given marker at some point in the string
    private String findYoutubeIdAfterMarker(String requestStr, String marker) {
        int youtuBeIdx = requestStr.indexOf(marker);
        if(youtuBeIdx > 0) {
            return findYoutubeIdPrefix(requestStr.substring(youtuBeIdx + marker.length()));
        } else {
            return null;
        }
    }

    //given a string that might begin with a youtube id, return the id if it does begin with one
    private String findYoutubeIdPrefix(String strWithPossibleIdPrefix) {
        int idStart = 0;
        int idEnd = 11;
        if (strWithPossibleIdPrefix.length() >= idEnd) {
            String potentialId = strWithPossibleIdPrefix.substring(idStart, idEnd);
            Matcher m = idPattern.matcher(potentialId);
            if(m.matches()) {
                return potentialId;
            }
        }
        return null;
    }

    private void updateQueuesForLeavers() {
        if(conf.getSecondaryQueueCountdownSeconds() == 0) {
            return;
        }
        try {
            //any song requested by someone who has been gone for long enough should move from the primary list to the secondary list

            ArrayList<SongEntry> newSongList = new ArrayList<>();
            irc.updateUserActiveTimes();
            for (SongEntry song : songList) {
                DateTime leaveTime = irc.lastActiveTimeByUser.get(song.getUser());

                if (leaveTime != null && leaveTime.plusSeconds(conf.getSecondaryQueueCountdownSeconds()).isBeforeNow()) {
                    logger.info("Bumping songid " + song.getRequestId() + " to secondary queue because its requester (" + song.getUser() + ") has been gone for " + conf.getSecondaryQueueCountdownSeconds() + " seconds");
                    secondarySongList.add(song);
                } else {
                    newSongList.add(song);
                }
            }
            songList = newSongList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateSongList() throws IOException {

        //send file to dropbox
        DbxClientV2 client = getDbxClient();
        try {
            String dboxContents = buildReportString();
            byte[] contentBytes = dboxContents.getBytes("utf-8");
            client.files().uploadBuilder(dboxFilePath).withMode(WriteMode.OVERWRITE).uploadAndFinish(new ByteArrayInputStream(contentBytes));
        } catch (DbxException e) {
            logger.error("Problem talking to dropbox to update the songlist", e);
        }
    }

    //go write to the filesystem so the user can overlay this text file on their stream if they want
    private void updateNowPlayingFile(SongEntry currentSong) {
        String nowPlaying;
        if(currentSong != null) {
            nowPlaying = conf.getNowPlayingPattern();
            if(StringUtils.isBlank(nowPlaying)) {
                //if there's no pattern to write, just don't do anything
                return;
            }
            nowPlaying = nowPlaying.replace("%title%", currentSong.getTitle());
            nowPlaying = nowPlaying.replace("%user%", currentSong.getUser());
            nowPlaying = nowPlaying.replace("%length%", currentSong.buildDurationStr());
        } else {
            //empty out the file if there's no current song
            nowPlaying = "";
        }

        try {
            FileUtils.writeStringToFile(new File(nowPlayingFilePath), nowPlaying, false);
        } catch (IOException e) {
            logger.warn("couldn't update nowPlayingInfo.txt", e);
        }
    }

    private String buildReportString() {
        StringBuilder sb = new StringBuilder();
        int runningSeconds = 0;

        if(currentSong != null) {
            runningSeconds += currentSong.getDurationSeconds();
            sb.append("Now playing:\n");
            appendSongReportEntry(sb, currentSong);
            sb.append("\n");
        }
        sb.append("============\n");
        if(songList.size() > 0) {
            sb.append("Main list:\n============\n");
            for(SongEntry song : songList) {
                appendSongReportEntry(sb, song);
                appendPlaysNextInfo(sb, runningSeconds);
                sb.append("\n\n" );
                runningSeconds += song.getDurationSeconds();
            }
        }
        if(secondarySongList.size() > 0) {
            sb.append("\n\nSecondary list:\n============\n");
            for(SongEntry song : secondarySongList) {
                appendSongReportEntry(sb, song);
                appendPlaysNextInfo(sb, runningSeconds);
                sb.append(" (if nothing is requested)\n\n" );
                runningSeconds += song.getDurationSeconds();
            }
        }

        runningSeconds = 0;
        if(lastPlayedSongs.size() > 0) {
            sb.append("\n\nPreviously played songs:\n============\n");
            for(int i = lastPlayedSongs.size()-1; i >=0; --i) {
                SongEntry song = lastPlayedSongs.get(i);
                appendSongReportEntry(sb, song);
                appendPlaysNextInfo(sb, runningSeconds);
                sb.append(" ago\n\n" );
                runningSeconds += song.getDurationSeconds();
            }
        }


        return sb.toString();
    }

    private void appendSongReportEntry(StringBuilder sb, SongEntry song) {
        sb.append(song.buildSongUrl()).append(" \"")
                .append(song.getTitle())
                .append("\", requested by ").append(song.getUser())
                .append(", id: ").append(song.getRequestId());

    }

    private void appendPlaysNextInfo(StringBuilder sb, int runningSeconds) {
        sb.append(", about ").append(runningSeconds / 60).append(" minutes");
    }

    private void doYoutubeRequest(String sender, String youtubeId, int startSeconds) {
        updateQueuesForLeavers();

        if(blacklistedYoutubeIds.contains(youtubeId)) {
            denySong(sender, "that song is blacklisted by the streamer");
            return;
        }


    }

    private void doSoundcloudRequest(String sender, String requestStr, int startSeconds) {
        updateQueuesForLeavers();
        try {

            JSONObject attributes = getJsonFromSoundcloud(requestStr);
            if(attributes == null) {
                denySong(sender, "I couldn't get info about that song from soundcloud");
                return;
            }

            String kind = attributes.optString("kind");
            
            if(!kind.equals("track")) {
                denySong(sender, "that doesn't look like a song");
                return;
            }
            
            String soundcloudId = attributes.getString("permalink_url").replaceFirst("https://soundcloud.com", "");

            if(blacklistedYoutubeIds.contains(soundcloudId)) {
                denySong(sender, "that song is blacklisted by the streamer");
                return;
            }

            int durationMillis = attributes.optInt("duration", 0);
            int durationSeconds = durationMillis / 1000;
            String title = attributes.optString("title", "<title not found>");

            if(!sender.equals(streamer) && durationSeconds / 60.0 > songLengthAllowedMinutes()) {
                denySong(sender, "the song is over " + songLengthAllowedMinutes() + " minutes");
                return;
            }

            if(!sender.equals(streamer) && conf.getQueueSize() > 0 && songList.size() >= conf.getQueueSize()) {
                denySong(sender, "the queue is full at " + conf.getQueueSize());
                return;
            }

            if(!sender.equals(streamer) && idCountInMainList(soundcloudId) > 0) {
                denySong(sender, "the song \"" + title + "\" is already in the queue");
                return;
            }

            if(!sender.equals(streamer) && currentSong != null && soundcloudId.equals(currentSong.getVideoId())) {
                denySong(sender, "the song \"" + title + "\" is currently playing");
                return;
            }

            if(!sender.equals(streamer) && songIsProhibitedByRecentDaysPolicy(soundcloudId, title)) {
                denySong(sender, "the song \"" + title + "\" has been played in the last " + conf.getRecencyDays() + " days");
                return;
            }

            if(!sender.equals(streamer) && conf.getMaxSongsPerUser() > 0 && senderCount(sender) >= conf.getMaxSongsPerUser()) {
                denySong(sender, "you have " + conf.getMaxSongsPerUser() + " songs in the queue already");
                return;
            }

            if(!sender.equals(streamer) && moveToPrimaryIfSongInSecondary(soundcloudId)) {
                irc.message(sender + ": bumping \"" + title + "\" to main queue" );
                return;
            }

            //this will cause a redundant looking double message if "up next" messages are enabled and there is no song playing.
            //but that's better than the alternative of missing the message entirely if no songs are playing at all and the player is not open
            //(which is a common scenario when someone first sets up their bot)
            irc.message(sender + ": added \"" + title + "\" to queue. id: " + nextRequestId);

            SongEntry newSong = new SongEntry(title, soundcloudId, -1, sender, new Date().getTime(), durationSeconds, false, startSeconds, SiteIds.SOUNDCLOUD);
            addSongToQueue(sender, newSong);


        } catch (Exception e) {
            logger.error("Problem with soundcloud request \"" + requestStr + "\"", e);
            denySong(sender, "I had an error while trying to add that song");
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

    private int addSongToQueue(String sender, SongEntry newSong) {
        newSong.setRequestId(nextRequestId);
        newSong.setUser(sender);

        lastRequestIdByUser.put(sender, nextRequestId);
        ++nextRequestId;
        int songsAdded = 1;
        try {
            if (newSong.isBackup()) {
                secondarySongList.add(newSong);
            } else {
                songList.add(newSong);
            }

            dao.addSong(newSong, true);
        } catch (Exception e) {
            logger.error("Exception while trying to add song with id " + (nextRequestId-1), e);
            denySong(sender, "There was an error while trying to add that song");
            songsAdded = 0;
        }
        if(songsAdded == 1) {
            try {
                updateSongList();
            } catch (Exception e) {
                logger.error("Error while updating song list after requesting song id " + (nextRequestId -1), e);
            }
        }
        return songsAdded;
    }
    
    public void setVolume(String trim) {
        try {
            int vol = Integer.parseInt(trim);
            if(vol >= 0 && vol <= 100) {
                volume = vol;
            }
        } catch (NumberFormatException e) {
            logger.error("Not a number: \"" + trim + "\"");
        }
    }

    private boolean moveToPrimaryIfSongInSecondary(String id) {
        for (SongEntry entry : secondarySongList) {
            if (entry.getVideoId().equals(id)) {
                songList.add(entry);
                secondarySongList.remove(entry);
                return true;
            }
        }
        return false;
    }

    private float songLengthAllowedMinutes() {
        if(songList.size() == 0) {
            return conf.getMaxSongLengthWhenQueueEmpty();
        } else {
            return conf.getMaxSongLength();
        }
    }

    private void denySong(String sender, String reason) {
        irc.message("Sorry " + sender + ", " + reason);
    }

    private int idCountInMainList(String id) {
        int count = 0;
        for(SongEntry entry : songList) {
            if(entry.getVideoId().equals(id)) {
                ++count;
            }
        }
        return count;
    }

    private int senderCount(String sender) {
        int count = 0;
        for(SongEntry entry : songList) {
            if(entry.getUser().equals(sender)) {
                ++count;
            }
        }
        return count;
    }

    private boolean songIsProhibitedByRecentDaysPolicy(String id, String title) {
        int days = conf.getRecencyDays();
        if(days == 0) {
            //feature turned off
            return false;
        }

        for(String recencyDayBypassTerm : conf.getRecencyDaysBypassTerms()) {
            if(title.toLowerCase().contains(recencyDayBypassTerm.toLowerCase())) {
                logger.info("Song " + id + " (" + title + ") ignoring recencyDays policy because it includes bypass term '" + recencyDayBypassTerm + "'");
                return false;
            }
        }

        long recencyCutoff = DateTime.now().minusDays(conf.getRecencyDays()).toDate().getTime();

        for(SongEntry entry : songHistory) {
            if(entry.getVideoId().equals(id) && entry.getRequestTime() > recencyCutoff ) {
                return true;
            }
        }
        return false;
    }

    public synchronized SongEntry startCurrentSong() {
        if(currentSong != null) {
            logger.info("I was asked for the current song; responding that it is id: " + currentSong.getRequestId() + " (no changes)");
            return currentSong;
        }
        return nextSong();
    }

    @Nullable
    public synchronized SongEntry nextSong() {

        if(currentSong != null) {
            lastPlayedSongs.add(currentSong);
            songHistory.add(currentSong);
            dao.setSongToBePlayed(currentSong.getRequestId(), false);
        }

        if(lastPlayedSongs.size() > conf.getSonglistHistoryLength()) {
            lastPlayedSongs.remove(0);
        }

        updateQueuesForLeavers();
        SongEntry song;
        String secondaryReport = "";
        //if the main queue is empty, pull from secondary
        if(songList.size() == 0) {
            if(secondarySongList.size() == 0) {
                currentSong = null;
                updateNowPlayingFile(currentSong);
                return null;
            }
            song = secondarySongList.remove(0);
            secondaryReport = " (from secondary queue)";
        } else {
            song = songList.remove(0);
        }

        if(conf.isShowUpNextMessages()) {
            irc.message("Up next: " + song.getTitle() + ", requested by: " + song.getUser() + ", duration " + song.buildDurationStr() + ", id: " + song.getRequestId() + secondaryReport);
        }

        currentSong = song;

        try {
            updateSongList();
            updateNowPlayingFile(currentSong);
        } catch (IOException e) {
            logger.error("Couldn't update playedSongs file", e);
        }

        return song;
    }



    public SongEntry getCurrentSong() {
        return currentSong;
    }

    public String getStreamer() {
        return streamer;
    }

    public int getVolume() {
        return volume;
    }

    public void likeSong() {
        SongEntry likedSong = currentSong;
        if(likedSong == null) {
            likedSong = songHistory.get(songHistory.size() - 1);
        }
        dao.setSongLiked(likedSong.getRequestId(), true);
        int score = dao.getUserScore(likedSong.getUser());
        //TODO: make this message configurable
        irc.message("The streamer liked a song. " + likedSong.getUser() + "'s score increases to " + score);
    }

    public synchronized DjState getStateRepresentation() {
        DjState state = new DjState(songList, secondarySongList, songHistory, currentSong, volume, nextRequestId, dropboxLink, irc.opUsernames);
        return state;
    }


}
