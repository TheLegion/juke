package jukebox.service;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import jukebox.entities.TrackState;
import one.util.streamex.StreamEx;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RecursiveAction;

class DownloadTask extends RecursiveAction {

    private final PlayerService playerService;
    private final Track track;

    DownloadTask(PlayerService playerService, Track track) {
        this.playerService = playerService;
        this.track = track;
    }

    @Override
    protected void compute() {
        Path trackPath = Paths.get(playerService.cacheDir, track.getId() + ".mp3");

        try {
            if (!Files.exists(trackPath)) {
                track.setState(TrackState.Downloading);
                playerService.notifyPlaylist();
                StreamEx.of(playerService.dataProviders)
                        .findFirst(provider -> provider.getSourceType() == track.getSource())
                        .map(provider -> provider.download(track))
                        .ifPresent(data -> saveTrack(trackPath, data));
            }
            track.setState(TrackState.Ready);
            track.setSource(TrackSource.Cache);
            playerService.notifyPlaylist();
        }
        catch (Exception e) {
            track.setState(TrackState.Failed);
            e.printStackTrace();
            playerService.notifyPlaylist();
        }
    }

    private void saveTrack(Path trackPath, InputStream stream) {
        try (OutputStream trackOutputStream = Files.newOutputStream(trackPath)) {
            IOUtils.copyLarge(stream, trackOutputStream);
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
                    Paths.get(playerService.cacheDir, "hashmap.txt"),
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
