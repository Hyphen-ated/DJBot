package hyphenated.djbot.json;

public class CheckResponse {

    int volume;
    int currentSongId;
    boolean paused;

    public CheckResponse( int volume, SongEntry currentSong, boolean paused) {
        this.volume = volume;
        if(currentSong == null) {
            this.currentSongId = 0;
        } else {
            this.currentSongId = currentSong.getRequestId();
        }
        this.paused = paused;
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

    public boolean getPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
