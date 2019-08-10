package jukebox.service;

import jukebox.api.TrackPosition;
import jukebox.core.ThreadPlayer;
import jukebox.entities.PlayerState;
import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import jukebox.entities.TrackState;
import one.util.streamex.StreamEx;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Port;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ThreadPlayer player;
    private String cacheDir;
    private byte volumeLevel = 100;
    private List<Track> playList = new ArrayList<>();
    private Track currentTrack;
    private List<DataProvider> dataProviders;
    private ForkJoinPool downloadPool = new ForkJoinPool(1);
    private List<Consumer<List<Track>>> playlistListeners = new ArrayList<>();
    private List<Consumer<Track>> currentTrackListeners = new ArrayList<>();
    private List<Consumer<Byte>> volumeListeners = new ArrayList<>();
    private Set<String> votedToSkip = new ConcurrentSkipListSet<>();
    private List<OutputStream> audioStreams = new ArrayList<>();

    public PlayerService(List<DataProvider> dataProviders, @Value("${cache.dir}") String cacheDir) {
        this.dataProviders = dataProviders;
        this.cacheDir = cacheDir;
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
        downloadPool.execute(new DownloadTask(track));
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
                case 4:
                    votesText = "голоса";
                    break;

                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 0:
                    votesText = "голосов";
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

    private void notifyPlaylist() {
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

        List<Track> tracksToRemove = playList.stream().filter(x -> x.getState() == TrackState.Failed)
                                             .collect(Collectors.toList());
        if (tracksToRemove.size() > 0) {
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
            InputStream stream = new WrappedInputStream(originalInputStream);
            player.setFile(stream);
            logger.info("Play now: {}", track);
            currentTrack.setState(TrackState.Playing);
            notifyCurrentTrack();
            if (Thread.currentThread() == player) {
                player.play();
            }
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
            }
            catch (Exception e) {
                track.setState(TrackState.Failed);
                e.printStackTrace();
            }
        }

        private void saveTrack(Path trackPath, InputStream stream) {
            try {
                IOUtils.copyLarge(stream, Files.newOutputStream(trackPath));
                stream.close();
                long duration = track.getDuration();
                String formattedDuration = LocalTime.ofSecondOfDay(duration)
                                                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                List<String> str = Collections.singletonList(
                        track.getId()
                                + "|" + track.getSinger().trim()
                                + "|" + track.getTitle().trim()
                                + "|" + formattedDuration
                );
                Files.write(
                        Paths.get(cacheDir, "hashmap.txt"),
                        str,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.CREATE
                );
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class WrappedInputStream extends InputStream {
        InputStream innerStream;

        WrappedInputStream(InputStream innerStream) {
            this.innerStream = innerStream;
        }

        @Override
        public int read() throws IOException {
            return innerStream.read();
        }

        @Override
        public String toString() {
            return innerStream.toString();
        }

        @Override
        public synchronized void reset() throws IOException {
            innerStream.reset();
        }

        @Override
        public void close() throws IOException {
            innerStream.close();
        }

        @Override
        public long skip(long n) throws IOException {
            return innerStream.skip(n);
        }

        @Override
        public int available() throws IOException {
            return innerStream.available();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = innerStream.read(b, off, len);
            ListIterator<OutputStream> iterator = audioStreams.listIterator();
            while (iterator.hasNext()) {
                OutputStream stream = iterator.next();
                try {
                    stream.write(b);
                }
                catch (IOException e) {
                    iterator.remove();
                }
            }
            return read;
        }

        @Override
        public int read(byte[] b) throws IOException {
            return innerStream.read(b);
        }

        @Override
        public synchronized void mark(int readlimit) {
            innerStream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return innerStream.markSupported();
        }
    }
}
