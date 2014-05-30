package hyphenated.djbot.json;

/**
 * Created with IntelliJ IDEA.
 * User: sean
 * Date: 5/29/14
 * Time: 9:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class NextResponse {
    String status;
    SongEntry song;

    public NextResponse(String status) {
        this.status = status;
    }

    public NextResponse(String status, SongEntry song) {
        this.status = status;
        this.song = song;
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
}
