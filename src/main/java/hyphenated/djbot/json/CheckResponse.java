package hyphenated.djbot.json;

public class CheckResponse {

    int volume;
    int currentSongId;

    public CheckResponse( int volume, SongEntry currentSong) {
        this.volume = volume;
        if(currentSong == null) {
            this.currentSongId = 0;
        } else {
            this.currentSongId = currentSong.getRequestId();
        }
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public int getCurrentSongId() {
        return currentSongId;
    }

    public void setCurrentSongId(int currentSongId) {
        this.currentSongId = currentSongId;
    }
}
