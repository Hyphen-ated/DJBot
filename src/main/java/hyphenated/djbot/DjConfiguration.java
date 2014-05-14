package hyphenated.djbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;


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
    @NotEmpty
    private String dropboxLink;

    @JsonProperty
    public String getChannel() {
        return channel;
    }

    @JsonProperty
    public void setChannel(String channel) {
        this.channel = channel;
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
    public String getDropboxLink() {
        return dropboxLink;
    }

    @JsonProperty
    public void setDropboxLink(String dropboxLink) {
        this.dropboxLink = dropboxLink;
    }

    //    public static void init() throws IOException {
//        Properties secrets = new Properties();
//        secrets.load(DjConfiguration.class.getResourceAsStream("/secrets.properties"));
//
//        Properties props = new Properties();
//        props.load(DjConfiguration.class.getResourceAsStream("/options.properties"));
//
//        twitchOauthToken = secrets.getProperty("TWITCH_OAUTH_TOKEN");
//        dropboxAccessToken = secrets.getProperty("DROPBOX_ACCESS_TOKEN");
//        channel = props.getProperty("djbot.channel");
//        queueSize = readInt("djbot.queueSize", 20);
//        recencyDays = readInt("djbot.recencyDays", 3);
//        botName = props.getProperty("djbot.botName");
//        queueHistoryFilePath = props.getProperty("djbot.queueHistoryFile");
//        unplayedSongsFilePath = props.getProperty("djbot.unplayedSongsFile");
//        maxSongsPerUser = readInt("djbot.maxSongsPerUser", 2);
//        dropboxLink = props.getProperty("djbot.dropboxLink");
//
//    }
//
//    private static int readInt(String property, int defaultVal) {
//        try {
//            return Integer.parseInt(property);
//        } catch (NumberFormatException e) {
//            System.out.println(property + " isn't a number");
//            return defaultVal;
//        }
//    }
}
