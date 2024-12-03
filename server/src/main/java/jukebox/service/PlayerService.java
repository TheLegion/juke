package jukebox.service;

import jakarta.servlet.ServletOutputStream;
import java.util.stream.Collectors;
import javax.sound.sampled.Mixer.Info;
import jukebox.api.TrackPosition;
import jukebox.core.ThreadPlayer;
import jukebox.entities.PlayerState;
import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import jukebox.entities.TrackState;
import jukebox.service.providers.DataProvider;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlayerService {

    public final String cacheDir;
    public final List<DataProvider> dataProviders;
    public final List<OutputStream> audioStreams = new ArrayList<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ThreadPlayer player;
    private final List<Track> playList = new ArrayList<>();
    private final ForkJoinPool downloadPool = new ForkJoinPool(1);
    private final List<Consumer<List<Track>>> playlistListeners = new ArrayList<>();
    private final List<Consumer<Track>> currentTrackListeners = new ArrayList<>();
    private final List<Consumer<Byte>> volumeListeners = new ArrayList<>();
    private final Set<String> votedToSkip = new ConcurrentSkipListSet<>();
    private final Line outline;

    private byte volumeLevel = 100;
    private Track currentTrack;

    public PlayerService(
            List<DataProvider> dataProviders,
            @Value("${audio.name}") String audioName,
            @Value("${cache.dir}") String cacheDir
    ) {
        this.dataProviders = dataProviders;
        this.cacheDir = cacheDir;

        Mixer.Info mixerInfo = Arrays.stream(AudioSystem.getMixerInfo())
                                     .filter(mixer -> mixer.getName().equals(audioName))
                                     .findFirst()
                                     .orElse(null);
        Mixer selectedMixer = AudioSystem.getMixer(mixerInfo);
        printMixersToLog(selectedMixer);

        try {
            outline = selectedMixer.getLine(selectedMixer.getSourceLineInfo()[0]);
        }
        catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        try {
            outline.open();
            FloatControl volumeControl = (FloatControl) outline.getControl(FloatControl.Type.MASTER_GAIN);
            volumeLevel = (byte) dbToVolume(
                    volumeControl.getValue(),
                    volumeControl.getMinimum(),
                    volumeControl.getMaximum()
            );
        }
        catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }
        player = new ThreadPlayer(this::playNext);
        player.start();
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
        downloadPool.execute(new DownloadTask(this, track));
    }

    public PlayerState getState() {
        return new PlayerState(playList, currentTrack, volumeLevel, this.player.getPlayDuration());
    }

    public String skip(String ip) {
        if (currentTrack == null) {
            return "Нечего скиповать.";
        }
        if (currentTrack.isRandomlyChosen()) {
            player.skip();
            return "";
        }
        if (votedToSkip.contains(ip)) {
            return "Ты уже голосовал против этой песни. Агитируй!";
        }
        votedToSkip.add(ip);
        int needToSkip = 4;
        if (votedToSkip.size() >= needToSkip) {
            player.skip();
        } else {
            int needVotesToSkip = needToSkip - votedToSkip.size();
            String votesText = "";
            switch (needVotesToSkip % 10) {
                case 1:
                    votesText = "голос";
                    break;

                case 2:
                case 3:
                    votesText = "голоса";
                    break;
            }
            return "Нужно ещё " + needVotesToSkip + " " + votesText + ". Агитируй!";
        }
        return "";
    }

    public synchronized void togglePlay() {
        if (currentTrack == null) {
            return;
        }
        if (currentTrack.getState() == TrackState.Ready) {
            play();
        } else if (currentTrack.getState() == TrackState.Playing) {
            pause();
        }
    }

    public void setVolume(byte volume) {
        if (volume < 20) {
            volume = 20;
        } else if (volume > 100) {
            volume = 100;
        }
        volumeLevel = volume;

        FloatControl volumeControl = (FloatControl) outline.getControl(FloatControl.Type.MASTER_GAIN);
        volumeControl.setValue(volumeToDb(volume, volumeControl.getMinimum(), volumeControl.getMaximum()));
        notifyVolume();
    }

    public void setTrackPosition(TrackPosition trackPosition) {
        Track track = StreamEx.of(playList)
                              .findFirst(t -> t.getId().equals(trackPosition.trackId))
                              .orElseThrow(RuntimeException::new);
        playList.remove(track);
        playList.add(trackPosition.position, track);
        notifyPlaylist();
    }

    public void registerAudioStream(ServletOutputStream outputStream) {
        this.audioStreams.add(outputStream);
    }

    public void shuffle() {
        Collections.shuffle(playList);
        notifyPlaylist();
    }

    public void notifyPlaylist() {
        playlistListeners.forEach(listener -> listener.accept(playList));
    }

    private void notifyCurrentTrack() {
        currentTrackListeners.forEach(listener -> listener.accept(currentTrack));
    }

    private void notifyVolume() {
        volumeListeners.forEach(listener -> listener.accept(volumeLevel));
    }

    private void playNext() {
        Track track = playList.stream().filter(x -> x.getState() == TrackState.Ready).findFirst().orElse(null);
        if (track != null) {
            playList.remove(track);
            notifyPlaylist();
        } else {
            track = chooseRandom();
        }

        List<Track> tracksToRemove = playList.stream()
                                             .filter(x -> x.getState() == TrackState.Failed)
                                             .collect(Collectors.toList());
        if (!tracksToRemove.isEmpty()) {
            playList.removeAll(tracksToRemove);
            notifyPlaylist();
        }

        if (track != null) {
            playTrack(track);
        }
    }

    private void playTrack(Track track) {
        try {
            votedToSkip.clear();
            currentTrack = track;
            Path path = Paths.get(cacheDir, track.getId() + ".mp3");
            InputStream originalInputStream = Files.newInputStream(path);
            InputStream stream = new WrappedInputStream(this, originalInputStream);
            player.setFile(stream, outline);
            logger.info("Play now: {}", track);
            currentTrack.setState(TrackState.Playing);
            notifyCurrentTrack();
        }
        catch (IOException e) {
            e.printStackTrace();
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

    private void pause() {
        player.pause();
        currentTrack.setState(TrackState.Ready);
        notifyCurrentTrack();
    }

    private void play() {
        player.continuePlay();
        currentTrack.setState(TrackState.Playing);
        notifyCurrentTrack();
    }

    private void printMixersToLog(Mixer selectedMixer) {
        log.info("Доступные устройства вывода: \n{}", Arrays.stream(AudioSystem.getMixerInfo())
                                                            .map(Info::getName)
                                                            .map("\"%s\""::formatted)
                                                            .collect(Collectors.joining(", ", "[", "]")));

        log.info("Выбранное устройство вывода: {}", selectedMixer.getMixerInfo().getName());
    }

    private float volumeToDb(float volume, float min, float max) {
        float length = max - min;
        return (float) (min + (Math.log(volume) / Math.log(100.0F) * length));
    }

    private float dbToVolume(float db, float min, float max) {
        float length = max - min;
        return (float) Math.exp((db - min) / length * Math.log(100.0F));
    }

}
