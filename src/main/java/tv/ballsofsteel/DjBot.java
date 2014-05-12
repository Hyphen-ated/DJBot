package tv.ballsofsteel;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.jibble.pircbot.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DjBot extends PircBot {

    String label_songrequest = "!songrequest";
    String label_songs = "!songs";
    String label_skipsong = "!skipsong";
    String label_volume = "!volume";

    String channel = "#" + DjConfiguration.channel;

    ArrayList<SongEntry> songList;

    int volume = 30;

    private int nextRequestId = 1;


    public DjBot( ArrayList<SongEntry> songList, int nextRequestId) throws Exception {
        this.songList = songList;
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

    private void doSongRequest(String sender, String id) {
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
            int duration = data.getInt("duration");
            if(duration > 7 * 60) {
                sendMessage(channel, "Sorry " + sender + ", the song \"" + title + "\" is over 7 minutes");
                return;
            }

            if(songList.size() >= DjConfiguration.maxSize) {
                sendMessage(channel, "Sorry " + sender + ", the queue is full at " + DjConfiguration.maxSize);
                return;
            }

            if(idCount(id) > 0) {
                sendMessage(channel, "Sorry " + sender + ", that song is already in the queue");
                return;
            }

            if(senderCount(sender) >= 2) {
                sendMessage(channel, "Sorry " + sender + ", you have 2 songs in the queue already");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            sendMessage(channel, sender + ": added \"" + title + "\" to queue");
            SongEntry newSong = new SongEntry(title, id, ++nextRequestId, sender);
            songList.add(newSong);

            String songJson = mapper.writeValueAsString(newSong);
            FileUtils.writeStringToFile(new File(DjConfiguration.queueHistoryFilePath), songJson + "\n", "utf-8", true);


        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }
    }

    private int idCount(String id) {
        int count = 0;
        for(SongEntry entry : songList) {
            if(entry.getVideoId() ==id) {
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


    public void doNewSong(SongEntry song) {
        sendMessage(channel, "Now playing: " + song.getTitle() + ", requested by: " + song.getUser());
        try {
            FileUtils.writeStringToFile(new File(DjConfiguration.queuePlaceFilePath), String.valueOf(song.getRequestId()), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }
}