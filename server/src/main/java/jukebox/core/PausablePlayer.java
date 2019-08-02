package jukebox.core;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackListener;

import java.io.InputStream;

public class PausablePlayer extends Thread {

    private final AdvancedPlayer player;

    public PausablePlayer(final InputStream inputStream, PlaybackListener listener) throws JavaLayerException {
        this.player = new AdvancedPlayer(inputStream);
        this.player.setPlayBackListener(listener);
    }

    @Override
    public void run() {
        try {
            this.player.play();
        }
        catch (JavaLayerException e) {
            e.printStackTrace();
        }
    }

}
