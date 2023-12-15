package jukebox.api;

import jukebox.entities.PlayerState;
import jukebox.entities.Track;
import jukebox.service.providers.DataProvider;
import jukebox.service.PlayerService;
import one.util.streamex.StreamEx;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Controller
public class MainController {

    private final List<DataProvider> dataProviders;
    private final PlayerService player;
    private final SimpMessagingTemplate brokerMessagingTemplate;

    MainController(
            List<DataProvider> dataProviders,
            PlayerService player,
            SimpMessagingTemplate brokerMessagingTemplate
    ) {
        this.dataProviders = dataProviders;
        this.player = player;
        this.brokerMessagingTemplate = brokerMessagingTemplate;
        player.onPlaylistChange(this::publishPlaylist);
        player.onCurrentTrackChange(this::publishCurrentTrack);
        player.onVolumeChange(this::publishVolume);
    }

    @SubscribeMapping("/search")
    public List<Track> search(@Header(required = false) String query) {
        String searchQuery = query == null ? "" : query;
        return StreamEx.of(dataProviders)
                       .flatMap(provider -> provider.search(searchQuery).stream())
                       .distinct(Track::getId)
                       .toList();
    }

    @MessageMapping("/player/add")
    public void addTrack(Track track) {
        this.player.add(track);
    }

    @SubscribeMapping("/player/state")
    public PlayerState getPlayerState() {
        return player.getState();
    }

    @SubscribeMapping("/player/skip")
    public String skipTrack(SimpMessageHeaderAccessor msg) {
        String ip = (String) Objects.requireNonNull(msg.getSessionAttributes()).get("ip");
        return this.player.skip(ip);
    }

    @MessageMapping("/player/toggle-play")
    public void play() {
        this.player.togglePlay();
    }

    @MessageMapping("/player/volume")
    public void changeVolume(byte volume) {
        this.player.setVolume(volume);
    }

    @MessageMapping("/player/position")
    public void setTrackPosition(TrackPosition trackPosition) {
        this.player.setTrackPosition(trackPosition);
    }

    @MessageMapping("/player/shuffle")
    public void shuffle() {
        this.player.shuffle();
    }

    @GetMapping("/audio/stream")
    public CompletableFuture<String> getAudioStream(HttpServletResponse response) throws IOException {
        response.setContentType("audio/mp3");
        response.flushBuffer();
        player.registerAudioStream(response.getOutputStream());
        return new CompletableFuture<>();
    }

    private void publishPlaylist(List<Track> playlist) {
        brokerMessagingTemplate.convertAndSend("/player/playlist", playlist);
    }

    private void publishCurrentTrack(Track track) {
        brokerMessagingTemplate.convertAndSend("/player/current", track);
    }

    private void publishVolume(Byte volume) {
        brokerMessagingTemplate.convertAndSend("/player/volume", volume);
    }

}
