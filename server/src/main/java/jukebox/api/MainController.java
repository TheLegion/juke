package jukebox.api;

import jukebox.entities.PlayerState;
import jukebox.entities.Track;
import jukebox.service.DataProvider;
import jukebox.service.PlayerService;
import one.util.streamex.StreamEx;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Objects;

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

    @MessageMapping("/search")
    @SendTo("/search/results")
    public List<Track> search(@Payload(required = false) String query) {
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

    @SubscribeMapping("/state")
    public PlayerState getPlayerState() {
        return player.getState();
    }

    @MessageMapping("/player/skip")
    @SendTo("/message")
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

    private void publishPlaylist(List<Track> playlist) {
        this.brokerMessagingTemplate.convertAndSend("/player/playlist", playlist);
    }

    private void publishCurrentTrack(Track track) {
        this.brokerMessagingTemplate.convertAndSend("/player/current", track);
    }

    private void publishVolume(Byte volume) {
        this.brokerMessagingTemplate.convertAndSend("/player/volume", volume);
    }

}
