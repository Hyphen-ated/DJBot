package hyphenated.djbot;

import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

import java.util.HashSet;


public class DjIrcBot extends PircBot {

    private final String channel;

    final String label_songrequest = "!songrequest";
    final String label_songlist = "!songlist";
    final String label_removesong = "!removesong";
    final String label_skipsong = "!skipsong";
    final String label_volume = "!volume";
    final String label_currentsong = "!currentsong";
    final String label_songs = "!songs";

    public volatile HashSet<String> opUsernames = new HashSet<>();

    private DjService dj;

    public DjIrcBot(DjConfiguration conf) {
        this.channel = "#" + conf.getChannel();


        this.setMessageDelay(conf.getMessageDelayMs());

        this.setName(conf.getBotName());


        try {
            this.connect("irc.twitch.tv", 6667, conf.getTwitchAccessToken());
        } catch (Exception e) {
            throw new RuntimeException("Couldn't connect to twitch irc", e);
        }

        this.joinChannel(channel);

    }

    public void setDjService(DjService dj) {
        this.dj = dj;
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

        if(modeChange.charAt(1) != 'o') {
            //we only care about ops, not other modes
            return;
        }

        if(modeChange.charAt(0) == '+') {
            opUsernames.add(changedUser);
        } else if (modeChange.charAt(0) == '-') {
            opUsernames.remove(changedUser);
        } else {
            System.out.println("Unexpected character in mode change: \"" + modeChange.charAt(0) + "\". expected + or -");
        }

    }

    @Override
    protected void onPart(String channel, String sender, String login, String hostname) {
        opUsernames.remove(sender);
    }

    @Override
    public void onMessage(String channel, String sender,
                          String login, String hostname, String message) {
        if(dj == null) {
            DjService.logger.error("DjIrcBot trying to handle a message but its djService hasn't been set");
        }

        message = message.trim();
        if (message.startsWith(label_songrequest)) {
            dj.irc_songRequest(sender, message.substring(label_songrequest.length()).trim());
        } else if (message.startsWith(label_songlist)) {
            dj.irc_songlist(sender);
        } else if (message.startsWith(label_removesong)) {
            dj.irc_removesong(sender, message.substring(label_removesong.length()).trim());
        } else if (message.startsWith(label_skipsong)) {
            //skipsong is the same as removesong
            dj.irc_removesong(sender, message.substring(label_skipsong.length()).trim());
        } else if (message.startsWith(label_volume)) {
            dj.irc_volume(sender, message.substring(label_volume.length()).trim());
        } else if (message.startsWith(label_currentsong)) {
            dj.irc_currentsong(sender);
        } else if (message.startsWith(label_songs)) {
            dj.irc_songs(sender);
        }
    }

    public void message(String msg) {
        sendMessage(channel, msg);
    }

    public User[] getUsers() {
        return this.getUsers(channel);
    }

    public boolean isMod(String sender) {
        return opUsernames.contains(sender);
    }
}
