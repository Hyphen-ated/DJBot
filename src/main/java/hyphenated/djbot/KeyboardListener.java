package hyphenated.djbot;

import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

public class KeyboardListener implements NativeKeyListener {
    boolean testMode;
    int skipSongKeyCode;
    int volumeUpKeyCode;
    int volumeDownKeyCode;
    int likeSongKeyCode;
    int playPauseKeyCode;
    DjService dj;
    public KeyboardListener(DjConfiguration conf, DjResource djResource) {
        testMode = conf.isKeyboardTestMode();
        skipSongKeyCode = conf.getSkipSongKeyCode();
        volumeUpKeyCode = conf.getVolumeUpKeyCode();
        volumeDownKeyCode = conf.getVolumeDownKeyCode();
        likeSongKeyCode = conf.getLikeSongKeyCode();
        playPauseKeyCode = conf.getPlayPauseKeyCode();
        dj = djResource.getDj();
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {
        
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        int code = nativeKeyEvent.getRawCode();
        if(testMode) {
            System.out.println("Keypress detected with this code: " + code);
        } else if (code == -1) {
            //make sure we do nothing, because "-1" in the config means "do nothing"
        } else if(code == volumeUpKeyCode) {
            int vol = dj.getVolume() + 10;
            dj.setVolume(vol);
        } else if(code == volumeDownKeyCode) {
            int vol = dj.getVolume() - 10;
            dj.setVolume(vol);
        } else if(code == skipSongKeyCode) {
            dj.nextSong();
        } else if(code == likeSongKeyCode) {
            dj.likeSong();
        } else if(code == playPauseKeyCode) {
            dj.playPause();
        }

    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {

    }
}
