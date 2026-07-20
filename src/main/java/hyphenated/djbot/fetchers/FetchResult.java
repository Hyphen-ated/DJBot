package hyphenated.djbot.fetchers;

import hyphenated.djbot.json.SongEntry;

import java.util.Arrays;
import java.util.List;

public class FetchResult {
    public final List<SongEntry> songs;
    public final String failureReason;
    public final int skippedSongs;
    public final String playlistTitle;

    public FetchResult(SongEntry song) {
        this.songs = Arrays.asList(song);
        this.skippedSongs = 0;
        this.failureReason = null;
        this.playlistTitle = null;
    }

    public FetchResult(List<SongEntry> songs, String playlistTitle, int skippedSongs) {
        this.songs = songs;
        this.skippedSongs = skippedSongs;
        this.failureReason = null;
        this.playlistTitle = playlistTitle;
    }

    public FetchResult(String failureReason) {
        this.songs = null;
        this.skippedSongs = 0;
        this.failureReason = failureReason;
        this.playlistTitle = null;
    }
}
