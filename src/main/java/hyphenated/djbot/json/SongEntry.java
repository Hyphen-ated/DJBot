package hyphenated.djbot.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class SongEntry {
    private String title;
    private String videoId;
    private int requestId;
    private String user;
    private long requestTime;
    private int durationSeconds;
    private boolean backup;
    private int startSeconds;

    public SongEntry() {

    }

    public SongEntry(String title, String videoId, int requestId, String user, long requestTime, int durationSeconds, boolean backup, int startSeconds) {
        this.title = title;
        this.videoId = videoId;
        this.requestId = requestId;
        this.user = user;
        this.requestTime = requestTime;
        this.durationSeconds = durationSeconds;
        this.backup = backup;
        this.startSeconds = startSeconds;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isBackup() {
        return backup;
    }

    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    public int getStartSeconds() {
        return startSeconds;
    }

    public void setStartSeconds(int startSeconds) {
        this.startSeconds = startSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SongEntry songEntry = (SongEntry) o;

        if (requestId != songEntry.requestId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return requestId;
    }

    public String toJsonString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (IOException e) {
            throw new RuntimeException("Can't convert a song entry to json", e);
        }
    }


    public String buildYoutubeUrl() {
        return "http://www.youtube.com/watch?v=" + videoId;
    }

    public String buildDurationStr() {
        String secs = String.valueOf(durationSeconds % 60);
        if(secs.length() == 1) {
            secs = "0" + secs;
        }
        return String.valueOf(durationSeconds / 60) + ":" + secs;
    }
}
