package hyphenated.djbot;

import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.joda.time.DateTime;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;


public class DjIrcBot extends PircBot {
    private final DjConfiguration conf;

    private final String channel;
    private final String twitchHostName;
    private final int twitchPort;

    final String label_songrequest = "!songrequest";
    final String label_sr = "!sr";
    final String label_soundcloud = "!soundcloud";
    final String label_sc = "!sc";
    final String label_songlist = "!songlist";
    final String label_sl = "!sl";
    final String label_removesong = "!removesong";
    final String label_skipsong = "!skipsong";
    final String label_volume = "!volume";
    final String label_currentsong = "!currentsong";
    final String label_lastsong = "!lastsong";
    final String label_nextsong = "!nextsong";
    final String label_songs = "!songs";
    final String label_songhelp = "!songhelp";
    final String label_wrongsong = "!wrongsong";
    final String label_songundo = "!songundo";
    final String label_songsearch = "!songsearch";
    final String label_songscore = "!songscore";


    public volatile HashSet<String> opUsernames = new HashSet<>();
    public volatile HashMap<String, DateTime> lastActiveTimeByUser = new HashMap<>();

    private DjService dj;

    public DjIrcBot(DjConfiguration conf, String twitchIrcHost) {
        this.conf = conf;
        this.channel = "#" + conf.getChannel();
        this.setMessageDelay(conf.getMessageDelayMs());
        this.setName(conf.getBotName());
        try {
            this.setEncoding("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        String[] parts = twitchIrcHost.split(":");
        if(parts.length != 2) {
            throw new RuntimeException("Twitch irc host '" + twitchIrcHost + "' should have exactly one colon in it, for the port number");
        }
        twitchHostName = parts[0];
        try {
            twitchPort = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new RuntimeException("The port for the twitch irc host is '" + parts[1] + "', but that's not a valid port number");
        }

    }


    public void startup() {
        tryConnect();
    }

    public void setDjService(DjService dj) {
        this.dj = dj;
    }

    private void setUserActiveTime(String user) {
        lastActiveTimeByUser.put(user, new DateTime());
    }

    private void tryConnect() {

        if(dj == null) {
            throw new IllegalStateException("DjIrcBot.startup() must be called after setDjService");
        }
        while(!isConnected()) {
            try {
                this.connect(this.twitchHostName, this.twitchPort, conf.getTwitchAccessToken());
                this.sendRawLine("CAP REQ :twitch.tv/membership");
                this.joinChannel(channel);
            } catch (Exception e) {
                dj.logger.error("Couldn't connect to twitch irc", e);
                //wait before trying again
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e1) {
                    //do nothing
                }
            }
        }
    }


    @Override
    protected void onDisconnect() {
        dj.logger.info("onDisconnect fired (if this message is spamming, doublecheck your botName and twitchAccessToken)");
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            //do nothing
        }
        tryConnect();
    }

    @Override
    protected void onUserMode(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String mode) {
        //this is a hack because pircbot doesn't properly parse twitch's MODE messages
        String mangledMessage = mode;
        //it looks like so: "#hyphen_ated +o 910dan" where hyphen_ated is the channel and 910dan is the op
        String[] pieces = mangledMessage.split(" ");
        String modeChannel = pieces[0];
        String modeChange = pieces[1];
        String changedUser = pieces[2];

        if(!modeChannel.equals(channel)) {
            //we don't care about modes in other channels than our streamer's
            return;
        }

        if (modeChange.charAt(1) != 'o') {
            //we only care about ops, not other modes
            return;
        }

        if (modeChange.charAt(0) == '+') {
            opUsernames.add(changedUser);
            dj.logger.info("Moderator priviliges added for " + changedUser);
        } else if (modeChange.charAt(0) == '-') {
            opUsernames.remove(changedUser);
            dj.logger.info("Moderator priviliges removed for " + changedUser);
        } else {
            dj.logger.warn("Unexpected character in mode change: \"" + modeChange.charAt(0) + "\". expected + or -");
        }

    }

    @Override
    protected void onPart(String channel, String sender, String login, String hostname) {
        if(opUsernames.contains(sender)) {
            opUsernames.remove(sender);
            dj.logger.info("Moderator " + sender + " left channel");
        }
        setUserActiveTime(sender);
    }

    @Override
    protected void onJoin(String channel, String sender, String login, String hostname) {
        setUserActiveTime(sender);
    }

    @Override
    public void onMessage(String channel, String sender,
                          String login, String hostname, String message) {
        if(dj == null) {
            dj.logger.error("DjIrcBot trying to handle a message but its djService hasn't been set");
            return;
        }
        setUserActiveTime(sender);

        message = message.trim();
        String lowercaseMessage = message.toLowerCase(Locale.US);
        if (lowercaseMessage.startsWith(label_songrequest)) {
            logMessage(sender, message);
            dj.irc_songRequest(sender, message.substring(label_songrequest.length()).trim());
        } else if (lowercaseMessage.startsWith(label_sr)) {
            logMessage(sender, message);
            dj.irc_songRequest(sender, message.substring(label_sr.length()).trim());
        } else if (lowercaseMessage.startsWith(label_soundcloud)) {
            logMessage(sender, message);
            dj.irc_soundcloud(sender, message.substring(label_soundcloud.length()).trim());
        } else if (lowercaseMessage.startsWith(label_sc)) {
            logMessage(sender, message);
            dj.irc_soundcloud(sender, message.substring(label_sc.length()).trim());
        } else if (lowercaseMessage.startsWith(label_songlist) || lowercaseMessage.startsWith(label_sl)) {
            logMessage(sender, message);
            dj.irc_songlist(sender);
        } else if (lowercaseMessage.startsWith(label_removesong)) {
            logMessage(sender, message);
            dj.irc_removesong(sender, message.substring(label_removesong.length()).trim());
        } else if (lowercaseMessage.startsWith(label_skipsong)) {
            logMessage(sender, message);
            dj.irc_removesong(sender, message.substring(label_skipsong.length()).trim());
        } else if (lowercaseMessage.startsWith(label_volume)) {
            logMessage(sender, message);
            dj.irc_volume(sender, message.substring(label_volume.length()).trim());
        } else if (lowercaseMessage.startsWith(label_currentsong)) {
            logMessage(sender, message);
            dj.irc_currentsong(sender);
        } else if (lowercaseMessage.startsWith(label_lastsong)) {
            logMessage(sender, message);
            dj.irc_lastsong(sender);
        } else if (lowercaseMessage.startsWith(label_nextsong)) {
            logMessage(sender, message);
            dj.irc_nextsong(sender);
        } else if (lowercaseMessage.startsWith(label_songsearch)) {
            logMessage(sender, message);
            dj.irc_songSearch(sender, message.substring(label_songsearch.length()).trim());
        } else if (lowercaseMessage.startsWith(label_songhelp)) {
            logMessage(sender, message);
            dj.irc_songs(sender);
        } else if (lowercaseMessage.startsWith(label_wrongsong)) {
            logMessage(sender, message);
            dj.irc_wrongsong(sender);
        } else if (lowercaseMessage.startsWith(label_songundo)) {
            logMessage(sender, message);
            dj.irc_wrongsong(sender);
        } else if (lowercaseMessage.startsWith(label_songscore)) {
            logMessage(sender, message);
            dj.irc_songscore(sender, message.substring(label_songscore.length()).trim());
        }
         //Put new things just above here. Songs has to be checked LAST because "!songs" is a prefix of other commands!
        
         else if (lowercaseMessage.startsWith(label_songs)) {
            logMessage(sender, message);
            dj.irc_songs(sender);
        }
    }

    private void logMessage(String sender, String message) {
        dj.logger.info("IRC: " + sender + ": " + message);
    }

    public void message(String msg) {
        msg = conf.getBotChatPrefix() + msg;
        sendMessage(channel, msg);
        logMessage(conf.getBotName(), msg);
    }

    public boolean isMod(String user) {
        return opUsernames.contains(user) || dj.getStreamer().equals(user);
    }

    public void updateUserActiveTimes() {
        DateTime now = new DateTime();
        for (User user : this.getUsers(channel)) {
            lastActiveTimeByUser.put(user.getNick().toLowerCase(Locale.US), now);
        }
    }
}
