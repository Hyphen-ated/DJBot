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

    final String label_songrequest = "!songrequest";
    final String label_songlist = "!songlist";
    final String label_removesong = "!removesong";
    final String label_skipsong = "!skipsong";
    final String label_volume = "!volume";
    final String label_currentsong = "!currentsong";
    final String label_lastsong = "!lastsong";
    final String label_nextsong = "!nextsong";
    final String label_songs = "!songs";

    public volatile HashSet<String> opUsernames = new HashSet<>();
    public volatile HashMap<String, DateTime> leaveTimeByUser = new HashMap<>();

    private DjService dj;

    public DjIrcBot(DjConfiguration conf) {
        this.conf = conf;
        this.channel = "#" + conf.getChannel();
        this.setMessageDelay(conf.getMessageDelayMs());
        this.setName(conf.getBotName());
        try {
            this.setEncoding("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


    public void startup() {
        tryConnect();
    }

    public void setDjService(DjService dj) {
        this.dj = dj;
    }


    private void tryConnect() {

        if(dj == null) {
            throw new IllegalStateException("DjIrcBot.startup() must be called after setDjService");
        }
        while(!isConnected()) {
            try {
                this.connect("irc.twitch.tv", 6667, conf.getTwitchAccessToken());
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
        leaveTimeByUser.put(sender, new DateTime());
    }

    @Override
    protected void onJoin(String channel, String sender, String login, String hostname) {
        if(leaveTimeByUser.containsKey(sender)) {
            leaveTimeByUser.remove(sender);
            dj.logger.info("User " + sender + " rejoined, removing them from the leaveTime map");
        }
    }

    @Override
    public void onMessage(String channel, String sender,
                          String login, String hostname, String message) {
        if(dj == null) {
            dj.logger.error("DjIrcBot trying to handle a message but its djService hasn't been set");
            return;
        }

        message = message.trim();
        String lowercaseMessage = message.toLowerCase(Locale.US);
        if (lowercaseMessage.startsWith(label_songrequest)) {
            logMessage(sender, message);
            dj.irc_songRequest(sender, message.substring(label_songrequest.length()).trim());
        } else if (lowercaseMessage.startsWith(label_songlist)) {
            logMessage(sender, message);
            dj.irc_songlist(sender);
        } else if (lowercaseMessage.startsWith(label_removesong)) {
            logMessage(sender, message);
            dj.irc_removesong(sender, message.substring(label_removesong.length()).trim());
        } else if (lowercaseMessage.startsWith(label_skipsong)) {
            //skipsong is the same as removesong
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
        } else if (lowercaseMessage.startsWith(label_songs)) {
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

    public User[] getUsers() {
        return this.getUsers(channel);
    }

    public boolean isMod(String sender) {
        return opUsernames.contains(sender) || dj.getStreamer().equals(sender);
    }
}
