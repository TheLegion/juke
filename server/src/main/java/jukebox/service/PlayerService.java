package jukebox.service;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import jukebox.core.PausablePlayer;
import jukebox.entities.PlayerState;
import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import jukebox.entities.TrackState;
import one.util.streamex.StreamEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Port;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PlayerService extends PlaybackListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${cache.dir}")
    private String cacheDir;

    @Value("${play.random}")
    private String playRandomFromCache;

    private byte volumeLevel = 100;
    private List<Track> playList = new ArrayList<>();
    private Track currentTrack;
    private List<DataProvider> dataProviders;

    private PausablePlayer player;
    private ForkJoinPool downloadPool = new ForkJoinPool();
    private List<Consumer<List<Track>>> playlistListeners = new ArrayList<>();
    private List<Consumer<Track>> currentTrackListeners = new ArrayList<>();
    private List<Consumer<Byte>> volumeListeners = new ArrayList<>();
    private List<String> votedToSkip = new ArrayList<>();

    public PlayerService(List<DataProvider> dataProviders) {
        this.dataProviders = dataProviders;
    }

    public void onPlaylistChange(Consumer<List<Track>> listener) {
        playlistListeners.add(listener);
    }

    public void onCurrentTrackChange(Consumer<Track> listener) {
        currentTrackListeners.add(listener);
    }

    public void onVolumeChange(Consumer<Byte> listener) {
        volumeListeners.add(listener);
    }

    public void add(Track track) {
        this.playList.add(track);
        downloadPool.execute(new DownloadTask(track));
    }

    private void notifyPlaylist() {
        playlistListeners.forEach(listener -> listener.accept(playList));
    }

    private void notifyCurrentTrack() {
        currentTrackListeners.forEach(listener -> listener.accept(currentTrack));
    }

    private void notifyVolume() {
        volumeListeners.forEach(listener -> listener.accept(volumeLevel));
    }

    @PostConstruct
    private void playNext() {
        Track track = playList.stream().filter(x -> x.getState() == TrackState.Ready).findFirst().orElse(null);
        if (track != null) {
            playList.remove(track);
            playTrack(track);
            notifyPlaylist();
            currentTrack = track;
            notifyCurrentTrack();
        } else if (playList.size() == 0 && Boolean.parseBoolean(playRandomFromCache)) {
            Track randomTrack = chooseRandom();
            if (randomTrack != null) {
                playTrack(randomTrack);
                currentTrack = randomTrack;
                notifyCurrentTrack();
            }
        }

        List<Track> tracksToRemove = playList.stream().filter(x -> x.getState() == TrackState.Failed)
                                             .collect(Collectors.toList());
        if (tracksToRemove.size() > 0) {
            playList.removeAll(tracksToRemove);
            notifyPlaylist();
        }
    }

    private void playTrack(Track track) {
        try {
            if (currentTrack != null) {
                player.stop();
                player = null;
                currentTrack = null;
                votedToSkip = new ArrayList<>();
            }
            Path path = Paths.get(cacheDir, track.getId() + ".mp3");
            currentTrack = track;
            player = new PausablePlayer(Files.newInputStream(path), this);
            logger.info("Play now: {}", track);
            player.start();
            currentTrack.setState(TrackState.Playing);
        }
        catch (JavaLayerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Track getRandomTrack() {
        return StreamEx.of(dataProviders)
                       .filter(x -> x.getSourceType() == TrackSource.Cache)
                       .findFirst()
                       .map(provider -> provider.search(""))
                       .filter(list -> !list.isEmpty())
                       .map(list -> list.get(new Random().nextInt(list.size())))
                       .orElse(null);
    }

    private Track chooseRandom() {
        Track t = this.getRandomTrack();
        if (t != null) {
            t.setState(TrackState.Ready);
            t.setRandomlyChosen(true);
        }
        return t;
    }

    public PlayerState getState() {
        return new PlayerState(playList, currentTrack, volumeLevel);
    }

    public String skip(String ip) {
        if (currentTrack == null) {
            return "Нечего скиповать.";
        }
        if (votedToSkip.contains(ip)) {
            return "Ты уже голосовал против этой песни. Агитируй!";
        }
        votedToSkip.add(ip);
        int needToSkip = currentTrack.isRandomlyChosen() ? 1 : 4;
        if (votedToSkip.size() >= needToSkip) {
            this.playNext();
        } else {
            return "Нужно ещё " + (needToSkip - votedToSkip.size()) + " голосов. Агитируй!";
        }
        return "";
    }

    private void pause() {
        if (player != null) {
            player.suspend();
            currentTrack.setState(TrackState.Ready);
            notifyCurrentTrack();
        }
    }

    private void play() {
        if (player != null) {
            player.resume();
            currentTrack.setState(TrackState.Playing);
            notifyCurrentTrack();
        }
    }

    public void togglePlay() {
        if (currentTrack != null) {
            if (currentTrack.getState() == TrackState.Ready) {
                play();
            } else if (currentTrack.getState() == TrackState.Playing) {
                pause();
            }
        }
    }

    public void setVolume(byte volume) {
        volumeLevel = volume;
        Port.Info source = Port.Info.SPEAKER;

        if (AudioSystem.isLineSupported(source)) {
            try {
                Port outline = (Port) AudioSystem.getLine(source);
                outline.open();
                FloatControl volumeControl = (FloatControl) outline.getControl(FloatControl.Type.VOLUME);
                volumeControl.setValue(volume / 100.0F);
            }
            catch (LineUnavailableException ex) {
                ex.printStackTrace();
            }
        }
        notifyVolume();
    }

    @Override
    public void playbackFinished(PlaybackEvent evt) {
        if (evt.getFrame() >= currentTrack.getDuration()) {
            player.stop();
            currentTrack = null;
            playNext();
        }
    }

    private class DownloadTask extends RecursiveAction {

        private Track track;

        DownloadTask(Track track) {
            this.track = track;
        }

        @Override
        protected void compute() {
            Path trackPath = Paths.get(cacheDir, track.getId() + ".mp3");

            try {
                if (!Files.exists(trackPath)) {
                    track.setState(TrackState.Downloading);
                    notifyPlaylist();
                    StreamEx.of(dataProviders)
                            .findFirst(provider -> provider.getSourceType() == track.getSource())
                            .map(provider -> provider.download(track))
                            .ifPresent(data -> saveTrack(trackPath, data));
                }
                track.setState(TrackState.Ready);
                track.setSource(TrackSource.Cache);
                notifyPlaylist();
                if (currentTrack == null) {
                    playNext();
                }
            }
            catch (Exception e) {
                track.setState(TrackState.Failed);
                e.printStackTrace();
            }
        }

        private void saveTrack(Path trackPath, byte[] data) {
            try {
                Files.write(trackPath, data);
                long duration = track.getDuration();
                String formattedDuration = LocalTime.ofSecondOfDay(duration)
                                                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                Files.writeString(
                        Paths.get(cacheDir, "hashmap.txt"),
                        track.getId()
                                + "|" + track.getSinger().trim()
                                + "|" + track.getTitle().trim()
                                + "|" + formattedDuration
                                + System.getProperty("line.separator"),
                        StandardOpenOption.APPEND,
                        StandardOpenOption.CREATE
                );
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
