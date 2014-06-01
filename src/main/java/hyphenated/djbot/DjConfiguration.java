package hyphenated.djbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import java.util.List;


public class DjConfiguration extends Configuration {
    @NotEmpty
    private String channel;

    private int queueSize;
    private int recencyDays;
    private int maxSongsPerUser;

    @Min(0)
    private long messageDelayMs;
    @NotEmpty
    private String botName;
    @NotEmpty
    private String twitchAccessToken;
    @NotEmpty
    private String dropboxAccessToken;
    private float maxSongLength;
    private float maxSongLengthWhenQueueEmpty;
    private int maxConsoleLines;
    @NotEmpty
    private String userCountryCode;
    private boolean showUpNextMessages;
    private List<String> blacklistedYoutubeIds;
    private int secondaryQueueCountdownSeconds;
    private int songlistHistoryLength;
    private int defaultVolume;



    //this should be the channel name without the # at the front. (the same as the streamer's twitch account name)
    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        if(channel.startsWith("#")) {
            this.channel = channel.substring(1);
        } else {
            this.channel = channel;
        }
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getRecencyDays() {
        return recencyDays;
    }

    public void setRecencyDays(int recencyDays) {
        this.recencyDays = recencyDays;
    }

    public int getMaxSongsPerUser() {
        return maxSongsPerUser;
    }

    public void setMaxSongsPerUser(int maxSongsPerUser) {
        this.maxSongsPerUser = maxSongsPerUser;
    }

    public long getMessageDelayMs() {
        return messageDelayMs;
    }

    public void setMessageDelayMs(long messageDelayMs) {
        this.messageDelayMs = messageDelayMs;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    public String getTwitchAccessToken() {
        return twitchAccessToken;
    }

    public void setTwitchAccessToken(String twitchAccessToken) {
        this.twitchAccessToken = twitchAccessToken;
    }

    public String getDropboxAccessToken() {
        return dropboxAccessToken;
    }

    public void setDropboxAccessToken(String dropboxAccessToken) {
        this.dropboxAccessToken = dropboxAccessToken;
    }

    public float getMaxSongLength() {
        return maxSongLength;
    }

    public void setMaxSongLength(float maxSongLength) {
        this.maxSongLength = maxSongLength;
    }

    public float getMaxSongLengthWhenQueueEmpty() {
        return maxSongLengthWhenQueueEmpty;
    }

    public void setMaxSongLengthWhenQueueEmpty(float maxSongLengthWhenQueueEmpty) {
        this.maxSongLengthWhenQueueEmpty = maxSongLengthWhenQueueEmpty;
    }

    public int getMaxConsoleLines() {
        return maxConsoleLines;
    }

    public void setMaxConsoleLines(int maxConsoleLines) {
        this.maxConsoleLines = maxConsoleLines;
    }

    public String getUserCountryCode() {
        return userCountryCode;
    }

    public void setUserCountryCode(String userCountryCode) {
        this.userCountryCode = userCountryCode;
    }

    public boolean isShowUpNextMessages() {
        return showUpNextMessages;
    }

    public void setShowUpNextMessages(boolean showUpNextMessages) {
        this.showUpNextMessages = showUpNextMessages;
    }

    public List<String> getBlacklistedYoutubeIds() {
        return blacklistedYoutubeIds;
    }

    public void setBlacklistedYoutubeIds(List<String> blacklistedYoutubeIds) {
        this.blacklistedYoutubeIds = blacklistedYoutubeIds;
    }

    public int getSecondaryQueueCountdownSeconds() {
        return secondaryQueueCountdownSeconds;
    }

    public void setSecondaryQueueCountdownSeconds(int secondaryQueueCountdownSeconds) {
        this.secondaryQueueCountdownSeconds = secondaryQueueCountdownSeconds;
    }

    public int getSonglistHistoryLength() {
        return songlistHistoryLength;
    }

    public void setSonglistHistoryLength(int songlistHistoryLength) {
        this.songlistHistoryLength = songlistHistoryLength;
    }

    public int getDefaultVolume() {
        return defaultVolume;
    }

    public void setDefaultVolume(int defaultVolume) {
        this.defaultVolume = defaultVolume;
    }
}
