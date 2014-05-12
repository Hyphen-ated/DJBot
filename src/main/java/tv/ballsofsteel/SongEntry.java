package tv.ballsofsteel;

import org.json.JSONObject;


public class SongEntry {
    private String title;
    private String videoId;
    private int requestId;
    private String user;

    public SongEntry() {

    }

    public SongEntry(String title, String videoId, int requestId, String user) {
        this.title = title;
        this.videoId = videoId;
        this.requestId = requestId;
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public String getVideoId() {
        return videoId;
    }

    public int getRequestId() {
        return requestId;
    }

    public String getUser() {
        return user;
    }       

    public String generateYoutubeUrl() {
        return "http://www.youtube.com/watch?" + videoId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setUser(String user) {
        this.user = user;
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

    public static SongEntry build(JSONObject historyElemObj) {
        return null;  //To change body of created methods use File | Settings | File Templates.
    }
}
