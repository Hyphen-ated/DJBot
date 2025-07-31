package hyphenated.djbot.json;

public class NextResponse {
    String status;
    SongEntry song;
    int queueLengthSeconds;

    public NextResponse(String status, int queueLengthSeconds) {
        this.status = status;
        this.queueLengthSeconds = queueLengthSeconds;
    }

    public NextResponse(String status, SongEntry song, int queueLengthSeconds) {
        this.status = status;
        this.song = song;
        this.queueLengthSeconds = queueLengthSeconds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public SongEntry getSong() {
        return song;
    }

    public void setSong(SongEntry song) {
        this.song = song;
    }

    public int getQueueLengthSeconds() {
        return queueLengthSeconds;
    }

    public void setQueueLengthSeconds(int queueLengthSeconds) {
        this.queueLengthSeconds = queueLengthSeconds;
    }
}
