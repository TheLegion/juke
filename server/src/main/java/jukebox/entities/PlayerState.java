package jukebox.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerState {
    private List<Track> playlist;
    private Track currentTrack;
    private byte volume;
    private long playDuration;
}
