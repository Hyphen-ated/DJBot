package hyphenated.djbot.fetchers;

import hyphenated.djbot.json.SongEntry;

import java.util.Arrays;
import java.util.List;

public class FetchResult {
    public final List<SongEntry> songs;
    public final String failureReason;

    public FetchResult(SongEntry song) {
        this.songs = Arrays.asList(song);
        this.failureReason = null;
    }

    public FetchResult(List<SongEntry> songs) {
        this.songs = songs;
        this.failureReason = null;
    }

    public FetchResult(String failureReason) {
        this.songs = null;
        this.failureReason = failureReason;
    }
}
