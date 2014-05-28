package hyphenated.djbot;

import java.util.ArrayList;
import java.util.HashSet;

public class DjState {

    volatile ArrayList<SongEntry> songList = new ArrayList<>();
    volatile ArrayList<SongEntry> secondarySongList = new ArrayList<>();
    volatile ArrayList<SongEntry> songHistory = new ArrayList<>();
    volatile SongEntry currentSong;

    volatile int volume;
    volatile int nextRequestId;
    String dropboxLink;

    volatile int songToSkip = 0;
    HashSet<String> opUsernames = new HashSet<>();

    public DjState() {

    }

    public DjState(ArrayList<SongEntry> songList, ArrayList<SongEntry> secondarySongList, ArrayList<SongEntry> songHistory, SongEntry currentSong, int volume, int nextRequestId, String dropboxLink, int songToSkip, HashSet<String> opUsernames) {
        this.songList = songList;
        this.secondarySongList = secondarySongList;
        this.songHistory = songHistory;
        this.currentSong = currentSong;
        this.volume = volume;
        this.nextRequestId = nextRequestId;
        this.dropboxLink = dropboxLink;
        this.songToSkip = songToSkip;
        this.opUsernames = opUsernames;
    }

    public ArrayList<SongEntry> getSongList() {
        return songList;
    }

    public void setSongList(ArrayList<SongEntry> songList) {
        this.songList = songList;
    }

    public ArrayList<SongEntry> getSecondarySongList() {
        return secondarySongList;
    }

    public void setSecondarySongList(ArrayList<SongEntry> secondarySongList) {
        this.secondarySongList = secondarySongList;
    }

    public ArrayList<SongEntry> getSongHistory() {
        return songHistory;
    }

    public void setSongHistory(ArrayList<SongEntry> songHistory) {
        this.songHistory = songHistory;
    }

    public SongEntry getCurrentSong() {
        return currentSong;
    }

    public void setCurrentSong(SongEntry currentSong) {
        this.currentSong = currentSong;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public int getNextRequestId() {
        return nextRequestId;
    }

    public void setNextRequestId(int nextRequestId) {
        this.nextRequestId = nextRequestId;
    }

    public String getDropboxLink() {
        return dropboxLink;
    }

    public void setDropboxLink(String dropboxLink) {
        this.dropboxLink = dropboxLink;
    }

    public int getSongToSkip() {
        return songToSkip;
    }

    public void setSongToSkip(int songToSkip) {
        this.songToSkip = songToSkip;
    }

    public HashSet<String> getOpUsernames() {
        return opUsernames;
    }

    public void setOpUsernames(HashSet<String> opUsernames) {
        this.opUsernames = opUsernames;
    }
}
