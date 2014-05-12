package hyphenated.djbot;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.jibble.pircbot.*;
import org.joda.time.DateTime;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DjBot extends PircBot {

    String label_songrequest = "!songrequest";
    String label_songs = "!songs";
    String label_skipsong = "!skipsong";
    String label_volume = "!volume";
    String label_currentsong = "!currentsong";


    String channel = "#" + DjConfiguration.channel;

    SongEntry currentSong;

    ArrayList<SongEntry> songList = new ArrayList<>();
    ArrayList<SongEntry> secondarySongList;

    ArrayList<SongEntry> songHistory;

    int volume = 30;
    private int nextRequestId;


    public DjBot( ArrayList<SongEntry> songList, ArrayList<SongEntry> songHistory, int nextRequestId) throws Exception {
        this.secondarySongList = songList;
        this.songHistory = songHistory;
        this.nextRequestId = nextRequestId;

        this.setName(DjConfiguration.botName);
        this.setMessageDelay(2000);
    }


    public void onMessage(String channel, String sender,
                          String login, String hostname, String message) {
        message = message.trim();
        if (message.startsWith(label_songrequest)) {
            songRequest( sender, message.substring(label_songrequest.length()).trim());
        } else if (message.startsWith(label_songs)) {
            songs( sender, message.substring(label_songs.length()).trim());
        } else if (message.startsWith(label_skipsong)) {
            skipsong( sender, message.substring(label_skipsong.length()).trim());
        } else if (message.startsWith(label_volume)) {
            volume( sender, message.substring(label_volume.length()).trim());
        } else if (message.startsWith(label_currentsong)) {
            currentsong(sender);
        }
    }

    private void currentsong(String sender) {
        if(currentSong == null) {
            sendMessage(channel, "No current song");
        } else {
            sendMessage(channel, "Current song: \"" + currentSong.getTitle() + "\", url: " + currentSong.generateYoutubeUrl());
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
        FileUtils.writeStringToFile(new File(DjConfiguration.unplayedSongsFilePath), unplayedSongsJson, "utf-8");
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

            if(songList.size() >= DjConfiguration.maxSize) {
                denySong(sender, "the queue is full at " + DjConfiguration.maxSize);
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
                denySong(sender, "the song \"" + title + "\" has been played in the last " + DjConfiguration.recencyDays + " days");
                return;
            }

            if(senderCount(sender) >= DjConfiguration.maxSongsPerUser) {
                denySong(sender, "you have 2 songs in the queue already");
                return;
            }

            if(moveToPrimaryIfSongInSecondary(id)) {
                sendMessage(channel, sender + ": bumping \"" + title + "\" to main queue");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            sendMessage(channel, sender + ": added \"" + title + "\" to queue");
            SongEntry newSong = new SongEntry(title, id, nextRequestId, sender, new Date().getTime());
            ++nextRequestId;
            songList.add(newSong);

            String songJson = mapper.writeValueAsString(newSong);
            FileUtils.writeStringToFile(new File(DjConfiguration.queueHistoryFilePath), songJson + "\n", "utf-8", true);

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
        long recencyCutoff = DateTime.now().minusDays(DjConfiguration.recencyDays).toDate().getTime();

        for(SongEntry entry : songHistory) {
            if(entry.getVideoId().equals(id) && entry.getRequestTime() > recencyCutoff ) {
                return true;
            }
        }
        return false;
    }

    private void songs( String sender, String trim) {
        //To change body of created methods use File | Settings | File Templates.
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


        sendMessage(channel, "Now playing: " + song.getTitle() + ", requested by: " + song.getUser() + secondaryReport);
        currentSong = song;

        try {
            updatePlayedSongsFile();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return song;
    }

}