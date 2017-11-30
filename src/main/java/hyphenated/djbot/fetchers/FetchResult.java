package hyphenated.djbot.fetchers;

import hyphenated.djbot.json.SongEntry;

import java.util.Arrays;
import java.util.List;

public class FetchResult {
    public final List<SongEntry> songs;
    public final String failureReason;
    public final int skippedSongs;

    public FetchResult(SongEntry song) {
        this.songs = Arrays.asList(song);
        this.skippedSongs = 0;
        this.failureReason = null;
    }

    public FetchResult(List<SongEntry> songs, int skippedSongs) {
        this.songs = songs;
        this.skippedSongs = skippedSongs;
        this.failureReason = null;
    }

    public FetchResult(String failureReason) {
        this.songs = null;
        this.skippedSongs = 0;
        this.failureReason = failureReason;
    }
}
