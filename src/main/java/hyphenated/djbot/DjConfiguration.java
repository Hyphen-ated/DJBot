package hyphenated.djbot;

import java.io.IOException;
import java.util.Properties;


public class DjConfiguration {
    public static String channel;
    public static String twitchOauthToken;
    public static String dropboxAccessToken;
    public static int maxSize;
    public static String botName;
    public static String queueHistoryFilePath;
    public static String unplayedSongsFilePath;
    public static int recencyDays;
    public static int maxSongsPerUser;
    public static String dropboxLink;

    public static void init() throws IOException {
        Properties secrets = new Properties();
        secrets.load(DjConfiguration.class.getResourceAsStream("/secrets.properties"));

        Properties props = new Properties();
        props.load(DjConfiguration.class.getResourceAsStream("/options.properties"));

        twitchOauthToken = secrets.getProperty("TWITCH_OAUTH_TOKEN");
        dropboxAccessToken = secrets.getProperty("DROPBOX_ACCESS_TOKEN");
        channel = props.getProperty("djbot.channel");
        maxSize = readInt("djbot.queueSize", 20);
        recencyDays = readInt("djbot.recencyDays", 3);
        botName = props.getProperty("djbot.botName");
        queueHistoryFilePath = props.getProperty("djbot.queueHistoryFile");
        unplayedSongsFilePath = props.getProperty("djbot.unplayedSongsFile");
        maxSongsPerUser = readInt("djbot.maxSongsPerUser", 2);
        dropboxLink = props.getProperty("djbot.dropboxLink");

    }

    private static int readInt(String property, int defaultVal) {
        try {
            return Integer.parseInt(property);
        } catch (NumberFormatException e) {
            System.out.println(property + " isn't a number");
            return defaultVal;
        }
    }
}
