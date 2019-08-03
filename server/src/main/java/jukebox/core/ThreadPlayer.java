package jukebox.core;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ThreadPlayer extends Thread {

    private final PlaybackListener listener;
    private AdvancedPlayer player;

    public ThreadPlayer(PlaybackListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        play();
    }

    public void play() {
        try {
            this.player.play();
        }
        catch (JavaLayerException e) {
            e.printStackTrace();
        }
    }

    public void setFile(Path path) {
        try {
            if (player != null && !(Thread.currentThread() instanceof ThreadPlayer)) {
                player.stop();
            }
            player = new AdvancedPlayer(Files.newInputStream(path));
            player.setPlayBackListener(listener);
        }
        catch (JavaLayerException | IOException e) {
            e.printStackTrace();
        }
    }

}
