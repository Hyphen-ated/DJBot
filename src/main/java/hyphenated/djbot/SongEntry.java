package hyphenated.djbot;


public class SongEntry {
    private String title;
    private String videoId;
    private int requestId;
    private String user;
    private long requestTime;
    private int durationSeconds;

    public SongEntry() {

    }

    public SongEntry(String title, String videoId, int requestId, String user, long requestTime, int durationSeconds) {
        this.title = title;
        this.videoId = videoId;
        this.requestId = requestId;
        this.user = user;
        this.requestTime = requestTime;
        this.durationSeconds = durationSeconds;
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

    public long getRequestTime() {
        return requestTime;
    }

    public int getDurationSeconds() {
        return durationSeconds;
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

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
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


    public String generateYoutubeUrl() {
        return "http://www.youtube.com/watch?" + videoId;
    }

}
