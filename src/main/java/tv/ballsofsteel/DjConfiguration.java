package tv.ballsofsteel;

import java.io.IOException;
import java.util.Properties;


public class DjConfiguration {
    public static String channel;
    public static String password;
    public static int maxSize;
    public static String botName;
    public static String queueHistoryFilePath;
    public static String unplayedSongsFilePath;
    public static int recencyDays;

    public static void init() throws IOException {
        Properties secrets = new Properties();
        secrets.load(DjConfiguration.class.getResourceAsStream("/secrets.properties"));

        Properties props = new Properties();
        props.load(DjConfiguration.class.getResourceAsStream("/options.properties"));

        password = secrets.getProperty("TWITCH_OAUTH_TOKEN");
        channel = props.getProperty("djbot.channel");
        try {
            maxSize = Integer.parseInt(props.getProperty("djbot.queueSize"));
        } catch (NumberFormatException e) {
            System.out.println("djbot.queueSize isn't a number");
            maxSize = 20;
        }
        try {
            recencyDays = Integer.parseInt(props.getProperty("djbot.recencyDays"));
        } catch (NumberFormatException e) {
            System.out.println("djbot.recencyDays isn't a number");
            recencyDays = 3;
        }
        botName = props.getProperty("djbot.botName");
        queueHistoryFilePath = props.getProperty("djbot.queueHistoryFile");
        unplayedSongsFilePath = props.getProperty("djbot.unplayedSongsFile");


    }
}
