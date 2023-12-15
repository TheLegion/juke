package jukebox.core;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;

import javax.sound.sampled.Line;
import java.io.InputStream;

public class ThreadPlayer extends Thread {

    private AdvancedPlayer player;
    private final Runnable onFinish;
    private long startedOnDate;
    private long startedOnDuration = 0;

    public ThreadPlayer(Runnable onFinish) {
        this.onFinish = onFinish;
        setName("ThreadPlayer");
    }

    @Override
    public void run() {
        play();
    }

    public void play() {
        try {
            while (true) {
                startedOnDate = System.currentTimeMillis();
                if (player != null) {
                    this.player.play();
                }
                onFinish.run();
                if (player == null) {
                    sleep(500);
                }
            }
        }
        catch (JavaLayerException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setFile(InputStream stream, Line outline) {
        try {
            player = new AdvancedPlayer(stream, new AudioDevice(outline));
            startedOnDuration = 0;
        }
        catch (JavaLayerException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        suspend();
        startedOnDuration += System.currentTimeMillis() - startedOnDate;
    }

    public void continuePlay() {
        resume();
        startedOnDate = System.currentTimeMillis();
    }

    public long getPlayDuration() {
        return (startedOnDuration + (System.currentTimeMillis() - startedOnDate)) / 1000;
    }

    public void skip() {
        if (player != null && !(Thread.currentThread() instanceof ThreadPlayer)) {
            player.close();
        }
    }

}
