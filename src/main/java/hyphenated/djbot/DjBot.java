package hyphenated.djbot;

import com.dropbox.core.*;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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

public class DjBot extends PircBot {

    final String label_songrequest = "!songrequest";
    final String label_songlist = "!songlist";
    final String label_skipsong = "!skipsong";
    final String label_volume = "!volume";
    final String label_currentsong = "!currentsong";
    final String label_songs = "!songs";
    final String queueHistoryFilePath = "queue.json";
    final String unplayedSongsFilePath = "unplayedSongs.json";


    private String channel;

    private SongEntry currentSong;

    private ArrayList<SongEntry> songList = new ArrayList<>();
    private ArrayList<SongEntry> secondarySongList = new ArrayList<>();

    private ArrayList<SongEntry> songHistory = new ArrayList<>();

    private int volume = 30;
    private int nextRequestId;
    private DjConfiguration conf;

    public DjBot( DjConfiguration newConf) {
        this.conf = newConf;
        this.channel = conf.getChannel();

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

        this.joinChannel(conf.getChannel());

        this.setMessageDelay(conf.getMessageDelayMs());
    }




    public void onMessage(String channel, String sender,
                          String login, String hostname, String message) {
        message = message.trim();
        if (message.startsWith(label_songrequest)) {
            songRequest( sender, message.substring(label_songrequest.length()).trim());
        } else if (message.startsWith(label_songlist)) {
            songlist(sender, message.substring(label_songlist.length()).trim());
        } else if (message.startsWith(label_skipsong)) {
            skipsong( sender, message.substring(label_skipsong.length()).trim());
        } else if (message.startsWith(label_volume)) {
            volume( sender, message.substring(label_volume.length()).trim());
        } else if (message.startsWith(label_currentsong)) {
            currentsong(sender);
        } else if (message.startsWith(label_songs)) {
            songs(sender);
        }
    }

    private void songs(String sender) {
        sendMessage(channel, sender + ": use \"!songrequest youtubeURL\" to request a song");
    }


    private void currentsong(String sender) {
        if(currentSong == null) {
            sendMessage(channel, "No current song (or the server just restarted)");
        } else {
            sendMessage(channel, "Current song: \"" + currentSong.getTitle() + "\", url: " + currentSong.buildYoutubeUrl());
        }
    }

    Pattern idPattern = Pattern.compile("[a-zA-Z0-9_-]{11}");

    void songRequest(String sender, String requestStr) {
        Matcher m = idPattern.matcher(requestStr);
        if (m.matches()) {
            doSongRequest(sender, requestStr);
            return;
        }
        int vIdx = requestStr.indexOf("v=");
        if (vIdx > 0) {
            int idStart = vIdx + 2;
            int idEnd = idStart + 11;
            if (requestStr.length() >= idEnd) {
                String potentialId = requestStr.substring(idStart, idEnd);
                m = idPattern.matcher(potentialId);
                if(m.matches()) {
                    doSongRequest(sender, potentialId );
                    return;
                }
            }
        }
        System.out.println("Bad request: " + requestStr);

    }

    private void updateQueuesForLeavers() {
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
            e.printStackTrace();
        }
    }

    private void updatePlayedSongsFile() throws IOException {
        //update the list of what songs have been played. anything currently in a queue has not been played
        ArrayList<Integer> unplayedSongs = new ArrayList<>();
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
        DbxRequestConfig config = new DbxRequestConfig("djbot/1.0", Locale.getDefault().toString());
        DbxClient client = new DbxClient(config, conf.getDropboxAccessToken());
        try {
            String dboxContents = buildReportString();
            byte[] contentBytes = dboxContents.getBytes("utf-8");
            DbxEntry.File uploadedFile = client.uploadFile("/Public/songlist.txt",
                    DbxWriteMode.force(), contentBytes.length, new ByteArrayInputStream(contentBytes));
        } catch (DbxException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private String buildReportString() {
        StringBuilder sb = new StringBuilder();
        int runningSeconds = 0;
        if(currentSong != null) {
            runningSeconds += currentSong.getDurationSeconds();
            sb.append("Now playing:\n");
            sb.append(currentSong.buildYoutubeUrl()).append(" \"").append(currentSong.getTitle()).append("\", requested by " + currentSong.getUser() + "\n");
        }
        sb.append("============\n");
        sb.append("Main list:\n============\n");
        for(SongEntry song : songList) {
            sb.append(song.buildYoutubeUrl()).append(" \"").append(song.getTitle()).append("\", requested by " + song.getUser() + ", plays in about " + runningSeconds / 60 + " minutes\n\n" );
            runningSeconds += song.getDurationSeconds();
        }
        sb.append("\n\nSecondary list:\n============\n");
        for(SongEntry song : secondarySongList) {
            sb.append(song.buildYoutubeUrl()).append(" \"").append(song.getTitle()).append("\", requested by " + song.getUser() + ", plays in about " + runningSeconds / 60 + " minutes (if nothing is requested)\n\n" );
            runningSeconds += song.getDurationSeconds();

        }
        return sb.toString();
    }

    private void doSongRequest(String sender, String id) {
        updateQueuesForLeavers();

        String infoUrl = "http://gdata.youtube.com/feeds/api/videos/" + id + "?v=2&alt=jsonc";
        GetMethod get = new GetMethod(infoUrl);
        HttpClient client = new HttpClient();
        try {
            int errcode = client.executeMethod(get);
            if(errcode != 200) {
                System.out.println("Got code " + errcode + " from " + infoUrl);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }

        try {
            String resp = get.getResponseBodyAsString();
            if(resp == null) {
                System.out.println("Couldn't get detail at " + infoUrl);
                return;
            }
            JSONObject obj = new JSONObject(resp);
            JSONObject data = obj.getJSONObject("data");
            String title = data.getString("title");
            int durationSeconds = data.getInt("duration");
            if(durationSeconds / 60.0 > songLengthAllowedMinutes()) {
                denySong(sender, "the song is over " + songLengthAllowedMinutes() + " minutes");
                return;
            }

            if(songList.size() >= conf.getQueueSize()) {
                denySong(sender, "the queue is full at " + conf.getQueueSize());
                return;
            }

            if(currentSong != null && id.equals(currentSong.getVideoId())) {
                denySong(sender, "the song \"" + title + "\" is currently playing");
                return;
            }

            if(idCountInMainList(id) > 0) {
                denySong(sender, "the song \"" + title + "\" is already in the queue");
                return;
            }

            if(idInRecentHistory(id)) {
                denySong(sender, "the song \"" + title + "\" has been played in the last " + conf.getRecencyDays() + " days");
                return;
            }

            if(senderCount(sender) >= conf.getMaxSongsPerUser()) {
                denySong(sender, "you have " + conf.getMaxSongsPerUser() + " songs in the queue already");
                return;
            }

            if(moveToPrimaryIfSongInSecondary(id)) {
                sendMessage(channel, sender + ": bumping \"" + title + "\" to main queue");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            sendMessage(channel, sender + ": added \"" + title + "\" to queue");
            SongEntry newSong = new SongEntry(title, id, nextRequestId, sender, new Date().getTime(), durationSeconds);
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
            return 12;
        } else {
            return 8;
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
        long recencyCutoff = DateTime.now().minusDays(conf.getRecencyDays()).toDate().getTime();

        for(SongEntry entry : songHistory) {
            if(entry.getVideoId().equals(id) && entry.getRequestTime() > recencyCutoff ) {
                return true;
            }
        }
        return false;
    }

    private void songlist( String sender, String trim) {
        sendMessage(channel, sender + ": see the song list at " + conf.getDropboxLink());
    }
    private void skipsong( String sender, String trim) {
        //To change body of created methods use File | Settings | File Templates.
    }

    private void volume( String sender, String trim) {

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




    public boolean noMoreSongs() {
        return songList.size() == 0 && secondarySongList.size() == 0;
    }

    public SongEntry nextSong() {

        updateQueuesForLeavers();

        boolean playingSecondary = false;
        SongEntry song;
        //if the main queue is empty, pull from secondary
        if(songList.size() == 0) {
            if(secondarySongList.size() == 0) {
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


        sendMessage(channel, "Up next: " + song.getTitle() + ", requested by: " + song.getUser() + ", duration " + song.buildDurationStr() + secondaryReport);
        currentSong = song;

        try {
            updatePlayedSongsFile();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return song;
    }

    public String getChannel() {
        return channel;
    }

    public int getVolume() {
        return volume;
    }
}