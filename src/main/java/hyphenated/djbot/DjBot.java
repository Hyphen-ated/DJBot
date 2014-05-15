package hyphenated.djbot;

import com.dropbox.core.*;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.jibble.pircbot.*;
import org.joda.time.DateTime;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//todo: split this into a DjBot and an DjIrcCommandHandler?
public class DjBot extends PircBot {

    final String label_songrequest = "!songrequest";
    final String label_songlist = "!songlist";
    final String label_removesong = "!removesong";
    final String label_skipsong = "!skipsong";
    final String label_volume = "!volume";
    final String label_currentsong = "!currentsong";
    final String label_songs = "!songs";
    final String queueHistoryFilePath = "queue.json";
    final String unplayedSongsFilePath = "unplayedSongs.json";

    final String dboxFilePath = "/Public/songlist.txt";
    private String streamer;
    private String channel;

    private volatile SongEntry currentSong;

    private volatile ArrayList<SongEntry> songList = new ArrayList<>();
    private volatile ArrayList<SongEntry> secondarySongList = new ArrayList<>();

    private volatile ArrayList<SongEntry> songHistory = new ArrayList<>();

    private volatile int volume = 30;
    private volatile int nextRequestId;
    private DjConfiguration conf;

    private String dropboxLink;

    private HashSet<String> blacklistedYoutubeIds = new HashSet<>();
    private volatile HashSet<String> opUsernames = new HashSet<>();
    private volatile int songToSkip = 0;

    @Override
    protected void onUserMode(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String mode) {
        //this is a hack because pircbot doesn't properly parse twitch's MODE messages
        String mangledMessage = mode;
        //it looks like so: "#hyphen_ated +o 910dan" where hyphen_ated is the channel and 910dan is the op
        String[] pieces = mangledMessage.split(" ");
        String modeChannel = pieces[0];
        String modeChange = pieces[1];
        String changedUser = pieces[2];

        if(!modeChannel.equals(channel)) {
            //we don't care about modes in other channels than our streamer's
            return;
        }

        if(modeChange.charAt(1) != 'o') {
            //we only care about ops, not other modes
            return;
        }

        if(modeChange.charAt(0) == '+') {
            opUsernames.add(changedUser);
        } else if (modeChange.charAt(0) == '-') {
            opUsernames.remove(changedUser);
        } else {
            System.out.println("Unexpected character in mode change: \"" + modeChange.charAt(0) + "\". expected + or -");
        }

    }

    protected void onPart(String channel, String sender, String login, String hostname) {
        opUsernames.remove(sender);
    }

    public DjBot( DjConfiguration newConf) {


        this.conf = newConf;
        this.channel = "#" + conf.getChannel();
        this.streamer = conf.getChannel();

        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GuiWindow.createAndShowGUI(conf.getMaxConsoleLines());
            }
        });

        List<String> history = null;
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

        int lastRequestId = 0;
        int lineNumber = 1;
        for(String historyElement : history) {
            SongEntry entry = null;
            try {
                entry = mapper.readValue(historyElement, SongEntry.class);
            } catch (IOException e) {
                throw new RuntimeException("File at " + queueHistoryFilePath + " has a problem at line " + lineNumber + ": the line won't parse to a json object with the right format.", e);
            }

            if(unplayedSongs.contains(entry.getRequestId())) {
                songList.add(entry);
            }
            //todo: configure 2 weeks here. or maybe have no cutoff? the point is so when the file gets huge we don't keep operating on the huge thing all day.
            long historyCutoff = DateTime.now().minusWeeks(2).toDate().getTime();
            if(entry.getRequestTime() > historyCutoff ) {
                songHistory.add(entry);
            }

            lastRequestId = Math.max(lastRequestId, entry.getRequestId());
            ++lineNumber;
        }

        this.nextRequestId = lastRequestId + 1;

        this.setVerbose(true);
        this.setName(conf.getBotName());

        try {
            this.connect("irc.twitch.tv", 6667, conf.getTwitchAccessToken());
        } catch (Exception e) {
            throw new RuntimeException("Couldn't connect to twitch irc", e);
        }

        this.joinChannel(channel);

        this.setMessageDelay(conf.getMessageDelayMs());

        this.dropboxLink = getDropboxLink(conf);
        if(conf.getBlacklistedYoutubeIds() != null) {
            this.blacklistedYoutubeIds.addAll(conf.getBlacklistedYoutubeIds());
        }
    }

    private String getDropboxLink(DjConfiguration conf) {
        DbxClient client = getDbxClient();
        try {
            return client.createShareableUrl(dboxFilePath);
        } catch (DbxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        }
    }

    private DbxClient getDbxClient() {
        DbxRequestConfig config = new DbxRequestConfig("djbot/1.0", Locale.getDefault().toString());
        return new DbxClient(config, conf.getDropboxAccessToken());
    }


    public void onMessage(String channel, String sender,
                          String login, String hostname, String message) {
        message = message.trim();
        if (message.startsWith(label_songrequest)) {
            irc_songRequest(sender, message.substring(label_songrequest.length()).trim());
        } else if (message.startsWith(label_songlist)) {
            irc_songlist(sender, message.substring(label_songlist.length()).trim());
        } else if (message.startsWith(label_removesong)) {
            irc_removesong(sender, message.substring(label_removesong.length()).trim());
        } else if (message.startsWith(label_skipsong)) {
            //skipsong is the same as removesong
            irc_removesong(sender, message.substring(label_skipsong.length()).trim());
        } else if (message.startsWith(label_volume)) {
            irc_volume(sender, message.substring(label_volume.length()).trim());
        } else if (message.startsWith(label_currentsong)) {
            irc_currentsong(sender);
        } else if (message.startsWith(label_songs)) {
            irc_songs(sender);
        }
    }

    private boolean isMod(String sender) {
        return this.opUsernames.contains(sender);
    }

    private void irc_songlist( String sender, String trim) {
        if(StringUtils.isEmpty(dropboxLink)) {
            sendMessage(channel, "Sorry " + sender + ", songlist isn't set up");
        } else {
            sendMessage(channel, sender + ": see the song list at " + dropboxLink);
        }
    }
    private void irc_removesong(String sender, String trim) {
        if(!isMod(sender)) {
            sendMessage(channel, "removing or skipping songs is for mods only");
            return;
        }
        try {
            int skipId = Integer.parseInt(trim);
            if(currentSong.getRequestId() == skipId) {
                nextSong();
            }
            removeSongFromList(songList, skipId, sender);
            removeSongFromList(secondarySongList, skipId, sender);
            songToSkip = skipId;
            try {
                updatePlayedSongsFile();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        } catch (NumberFormatException e) {
            sendMessage(channel, sender + ": you must specify the song id to remove or skip (a number)");
        }
    }

    private void removeSongFromList(List<SongEntry> listOfSongs, int skipId, String sender) {
        Iterator<SongEntry> entryIterator = listOfSongs.iterator();
        while (entryIterator.hasNext()) {
            SongEntry curEntry = entryIterator.next();
            if (curEntry.getRequestId() == skipId) {
                sendMessage(channel, sender + ": removed song \"" + curEntry.getTitle() + "\"");
                entryIterator.remove();
            }
        }
    }


    private void irc_volume(String sender, String trim) {
        if(!isMod(sender)) {
            sendMessage(channel, "Volume is for mods only");
            return;
        }
        try {
            int newVolume = Integer.parseInt(trim);
            if(newVolume < 1) {
                newVolume = 1;
            } else if (newVolume > 100) {
                newVolume = 100;
            }
            sendMessage(channel, sender + ": volume changed from " + volume + " to " + newVolume);
            volume = newVolume;
        } catch (NumberFormatException e) {
            sendMessage(channel, sender + ": volume must be between 1 and 100");
        }
    }

    private void irc_songs(String sender) {
        sendMessage(channel, sender + ": use \"!songrequest youtubeURL\" to request a song");
    }


    private void irc_currentsong(String sender) {
        if(currentSong == null) {
            sendMessage(channel, "No current song (or the server just restarted)");
        } else {
            sendMessage(channel, "Current song: \"" + currentSong.getTitle() + "\", url: " + currentSong.buildYoutubeUrl());
        }
    }




    Pattern idPattern = Pattern.compile("[a-zA-Z0-9_-]{11}");

    //given a string that a user songrequested, try to figure out what it is a link to and do the work to handle it
    void irc_songRequest(String sender, String requestStr) {
        Matcher m = idPattern.matcher(requestStr);
        //we support !songrequest <youtubeid>
        if (m.matches()) {
            doYoutubeRequest(sender, requestStr);
            return;
        }

        //we support youtu.be/<youtubeid>
        String youtuBeId = findYoutubeIdAfterMarker(requestStr, "youtu.be/");
        if(youtuBeId != null) {
            doYoutubeRequest(sender, youtuBeId);
            return;
        }

        //we support standard youtube links like https://www.youtube.com/watch?v=<youtubeid>
        String vParamId = findYoutubeIdAfterMarker(requestStr, "?v=");
        if(requestStr.contains("youtube.com") && vParamId != null) {
            doYoutubeRequest(sender, vParamId);
            return;
        }

        //we support youtube links like https://www.youtube.com/v/<youtubeid>
        String vPathId = findYoutubeIdAfterMarker(requestStr, "/v/");
        if(requestStr.contains("youtube.com") && vPathId != null) {
            doYoutubeRequest(sender, vPathId);
            return;
        }

        System.out.println("Bad request: " + requestStr);

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
        if(!conf.isBumpLeaverSongsToSecondaryQueue()) {
            return;
        }
        try {
            //any song requested by someone who isn't still here should move from the primary list to the secondary list
            User[] users = getUsers(channel);
            HashSet<String> userNamesPresent = new HashSet<>();
            for (User user : users) {
                userNamesPresent.add(user.getNick());
            }

            ArrayList<SongEntry> newSongList = new ArrayList<>();
            for (SongEntry song : songList) {
                if (userNamesPresent.contains(song.getUser())) {
                    newSongList.add(song);
                } else {
                    secondarySongList.add(song);
                }
            }
            songList = newSongList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updatePlayedSongsFile() throws IOException {
        //update the list of what songs have been played. anything currently playing or in a queue has not "been played"
        ArrayList<Integer> unplayedSongs = new ArrayList<>();
        if(currentSong != null) {
            unplayedSongs.add(currentSong.getRequestId());
        }
        for (SongEntry song : songList) {
            unplayedSongs.add(song.getRequestId());
        }
        for (SongEntry song : secondarySongList) {
            unplayedSongs.add(song.getRequestId());
        }

        ObjectMapper mapper = new ObjectMapper();
        String unplayedSongsJson = mapper.writeValueAsString(unplayedSongs);
        FileUtils.writeStringToFile(new File(this.unplayedSongsFilePath), unplayedSongsJson, "utf-8");


        //send file to dropbox
        DbxClient client = getDbxClient();
        try {
            String dboxContents = buildReportString();
            byte[] contentBytes = dboxContents.getBytes("utf-8");
            DbxEntry.File uploadedFile = client.uploadFile(dboxFilePath,
                    DbxWriteMode.force(), contentBytes.length, new ByteArrayInputStream(contentBytes));
        } catch (DbxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    private String buildReportString() {
        StringBuilder sb = new StringBuilder();
        int runningSeconds = 0;

        //todo: clean up this duplication
        if(currentSong != null) {
            runningSeconds += currentSong.getDurationSeconds();
            sb.append("Now playing:\n");
            sb.append(currentSong.buildYoutubeUrl()).append(" \"").append(currentSong.getTitle()).append("\", requested by " + currentSong.getUser() + ", id: " + currentSong.getRequestId() + "\n" );
        }
        sb.append("============\n");
        sb.append("Main list:\n============\n");
        for(SongEntry song : songList) {
            sb.append(song.buildYoutubeUrl()).append(" \"").append(song.getTitle()).append("\", requested by " + song.getUser() + ", id: " + song.getRequestId() + ", plays in about " + runningSeconds / 60 + " minutes\n\n" );
            runningSeconds += song.getDurationSeconds();
        }
        sb.append("\n\nSecondary list:\n============\n");
        for(SongEntry song : secondarySongList) {
            sb.append(song.buildYoutubeUrl()).append(" \"").append(song.getTitle()).append("\", requested by " + song.getUser() + ", id: " + song.getRequestId() + ", plays in about " + runningSeconds / 60 + " minutes (if nothing is requested)\n\n" );
            runningSeconds += song.getDurationSeconds();

        }
        return sb.toString();
    }

    private void doYoutubeRequest(String sender, String youtubeId) {
        updateQueuesForLeavers();

        if(blacklistedYoutubeIds.contains(youtubeId)) {
            denySong(sender, "that song is blacklisted by the streamer");
            return;
        }

        String infoUrl = "http://gdata.youtube.com/feeds/api/videos/" + youtubeId + "?v=2&alt=jsonc&restriction=" + conf.getUserCountryCode();
        GetMethod get = new GetMethod(infoUrl);
        HttpClient client = new HttpClient();
        try {
            int errcode = client.executeMethod(get);
            if(errcode != 200) {
                System.out.println("Song request error: got code " + errcode + " from " + infoUrl);
                denySong(sender, "I couldn't find info about that video on youtube");

                return;
            }
        } catch (IOException e) {
            System.out.println("Couldn't get info from youtube api. Stacktrace:");
            e.printStackTrace();
            denySong(sender, "I couldn't find info about that video on youtube");
            return;
        }

        try {
            String resp = get.getResponseBodyAsString();
            if(resp == null) {
                System.out.println("Couldn't get detail at " + infoUrl);
                denySong(sender, "I couldn't find info about that video on youtube");

                return;
            }
            JSONObject obj = new JSONObject(resp);
            JSONObject data = obj.getJSONObject("data");
            String title = data.getString("title");
            int durationSeconds = data.getInt("duration");
            JSONObject accessControl = data.getJSONObject("accessControl");
            String embedAllowed = accessControl.getString("embed");
            JSONObject status = data.optJSONObject("status");
            String restrictionReason = null;
            if(status != null) {
                restrictionReason = status.getString("reason");
            }

            if("requesterRegion".equals(restrictionReason)) {
                denySong(sender, "that video can't be played in the streamer's country");
                return;
            }

            if("private".equals(restrictionReason)) {
                denySong(sender, "that video is private");
                return;
            }

            if(!("allowed".equals(embedAllowed))) {
                denySong(sender, "that video is not allowed to be embedded");
                return;
            }

            if(!sender.equals(streamer) && durationSeconds / 60.0 > songLengthAllowedMinutes()) {
                denySong(sender, "the song is over " + songLengthAllowedMinutes() + " minutes");
                return;
            }

            if(conf.getQueueSize() > 0 && songList.size() >= conf.getQueueSize()) {
                denySong(sender, "the queue is full at " + conf.getQueueSize());
                return;
            }

            if(currentSong != null && youtubeId.equals(currentSong.getVideoId())) {
                denySong(sender, "the song \"" + title + "\" is currently playing");
                return;
            }

            if(idCountInMainList(youtubeId) > 0) {
                denySong(sender, "the song \"" + title + "\" is already in the queue");
                return;
            }

            if(!sender.equals(streamer) && idInRecentHistory(youtubeId)) {
                denySong(sender, "the song \"" + title + "\" has been played in the last " + conf.getRecencyDays() + " days");
                return;
            }

            if(!sender.equals(streamer) && conf.getMaxSongsPerUser() > 0 && senderCount(sender) >= conf.getMaxSongsPerUser()) {
                denySong(sender, "you have " + conf.getMaxSongsPerUser() + " songs in the queue already");
                return;
            }

            if(!sender.equals(streamer) && moveToPrimaryIfSongInSecondary(youtubeId)) {
                sendMessage(channel, sender + ": bumping \"" + title + "\" to main queue" );
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            sendMessage(channel, sender + ": added \"" + title + "\" to queue. id: " + nextRequestId);
            SongEntry newSong = new SongEntry(title, youtubeId, nextRequestId, sender, new Date().getTime(), durationSeconds);
            ++nextRequestId;
            songList.add(newSong);

            String songJson = mapper.writeValueAsString(newSong);
            FileUtils.writeStringToFile(new File(this.queueHistoryFilePath), songJson + "\n", "utf-8", true);

            updatePlayedSongsFile();

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }
    }

    public void setVolume(String trim) {
        try {
            int vol = Integer.parseInt(trim);
            if(vol >= 0 && vol <= 100) {
                volume = vol;
            }
        } catch (NumberFormatException e) {
            System.out.println("Not a number: \""+ trim +"\"");
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
        sendMessage(channel, "Sorry " + sender + ", " + reason);
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

    private boolean idInRecentHistory(String id) {
        int days = conf.getRecencyDays();
        if(days == 0) {
            //feature turned off
            return false;
        }
        long recencyCutoff = DateTime.now().minusDays(conf.getRecencyDays()).toDate().getTime();

        for(SongEntry entry : songHistory) {
            if(entry.getVideoId().equals(id) && entry.getRequestTime() > recencyCutoff ) {
                return true;
            }
        }
        return false;
    }

    public boolean noMoreSongs() {
        return songList.size() == 0 && secondarySongList.size() == 0;
    }

    public SongEntry startCurrentSong() {
        System.out.println("startCurrentSong");
        if(currentSong != null) {
            return currentSong;
        }
        updateQueuesForLeavers();
        if(songList.size() == 0) {
            if(secondarySongList.size() == 0) {
                return null;
            }
            currentSong = secondarySongList.remove(0);
        } else {
            currentSong = songList.remove(0);
        }
        return currentSong;
    }

    public SongEntry nextSong() {
        System.out.println("nextSong");

        updateQueuesForLeavers();

        boolean playingSecondary = false;
        SongEntry song;
                //if the main queue is empty, pull from secondary
        if(songList.size() == 0) {
            if(secondarySongList.size() == 0) {
                currentSong = null;
                return null;
            }
            song = secondarySongList.remove(0);
            playingSecondary = true;
            System.out.println("Playing secondary song \"" + song.getTitle() + "\"");
        } else {
            song = songList.remove(0);
        }

        String secondaryReport = "";
        if (playingSecondary) {
            secondaryReport = " (from secondary queue)";
        }

        if(conf.isShowUpNextMessages()) {
            sendMessage(channel, "Up next: " + song.getTitle() + ", requested by: " + song.getUser() + ", duration " + song.buildDurationStr() + ", id: " + song.getRequestId() + secondaryReport);
        }

        currentSong = song;

        try {
            updatePlayedSongsFile();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return song;
    }

    public SongEntry getCurrentSong() {
        return currentSong;
    }

    public String getChannel() {
        return channel;
    }

    public int getVolume() {
        return volume;
    }

    public int getSongToSkip() {
        return songToSkip;
    }
}