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


    //this should be the channel name without the # at the front. (the same as the streamer's twitch account name)
    @JsonProperty
    public String getChannel() {
        return channel;
    }

    @JsonProperty
    public void setChannel(String channel) {
        if(channel.startsWith("#")) {
            this.channel = channel.substring(1);
        } else {
            this.channel = channel;
        }
    }

    @JsonProperty
    public int getQueueSize() {
        return queueSize;
    }

    @JsonProperty
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    @JsonProperty
    public int getRecencyDays() {
        return recencyDays;
    }

    @JsonProperty
    public void setRecencyDays(int recencyDays) {
        this.recencyDays = recencyDays;
    }

    @JsonProperty
    public int getMaxSongsPerUser() {
        return maxSongsPerUser;
    }

    @JsonProperty
    public void setMaxSongsPerUser(int maxSongsPerUser) {
        this.maxSongsPerUser = maxSongsPerUser;
    }

    @JsonProperty
    public long getMessageDelayMs() {
        return messageDelayMs;
    }

    @JsonProperty
    public void setMessageDelayMs(long messageDelayMs) {
        this.messageDelayMs = messageDelayMs;
    }

    @JsonProperty
    public String getBotName() {
        return botName;
    }

    @JsonProperty
    public void setBotName(String botName) {
        this.botName = botName;
    }

    @JsonProperty
    public String getTwitchAccessToken() {
        return twitchAccessToken;
    }

    @JsonProperty
    public void setTwitchAccessToken(String twitchAccessToken) {
        this.twitchAccessToken = twitchAccessToken;
    }

    @JsonProperty
    public String getDropboxAccessToken() {
        return dropboxAccessToken;
    }

    @JsonProperty
    public void setDropboxAccessToken(String dropboxAccessToken) {
        this.dropboxAccessToken = dropboxAccessToken;
    }

    @JsonProperty
    public float getMaxSongLength() {
        return maxSongLength;
    }

    @JsonProperty
    public void setMaxSongLength(float maxSongLength) {
        this.maxSongLength = maxSongLength;
    }

    @JsonProperty
    public float getMaxSongLengthWhenQueueEmpty() {
        return maxSongLengthWhenQueueEmpty;
    }

    @JsonProperty
    public void setMaxSongLengthWhenQueueEmpty(float maxSongLengthWhenQueueEmpty) {
        this.maxSongLengthWhenQueueEmpty = maxSongLengthWhenQueueEmpty;
    }


    @JsonProperty
    public int getMaxConsoleLines() {
        return maxConsoleLines;
    }

    @JsonProperty
    public void setMaxConsoleLines(int maxConsoleLines) {
        this.maxConsoleLines = maxConsoleLines;
    }

    @JsonProperty
    public String getUserCountryCode() {
        return userCountryCode;
    }

    @JsonProperty
    public void setUserCountryCode(String userCountryCode) {
        this.userCountryCode = userCountryCode;
    }

    @JsonProperty
    public boolean isShowUpNextMessages() {
        return showUpNextMessages;
    }

    @JsonProperty
    public void setShowUpNextMessages(boolean showUpNextMessages) {
        this.showUpNextMessages = showUpNextMessages;
    }

    @JsonProperty
    public List<String> getBlacklistedYoutubeIds() {
        return blacklistedYoutubeIds;
    }

    @JsonProperty
    public void setBlacklistedYoutubeIds(List<String> blacklistedYoutubeIds) {
        this.blacklistedYoutubeIds = blacklistedYoutubeIds;
    }

    @JsonProperty
    public int getSecondaryQueueCountdownSeconds() {
        return secondaryQueueCountdownSeconds;
    }

    @JsonProperty
    public void setSecondaryQueueCountdownSeconds(int secondaryQueueCountdownSeconds) {
        this.secondaryQueueCountdownSeconds = secondaryQueueCountdownSeconds;
    }
}
