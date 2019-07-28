package jukebox.entities;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;

import java.net.URI;

@Getter
@Setter
public class Track {
    private String id;

    private String title;

    private String singer;

    private long duration;

    private TrackState state;

    private URI uri;

    private TrackSource source;

    private long playPosition;

    private boolean isRandomlyChosen;

    @Override
    public String toString() {
        return String.format("%s - %s", title, singer);
    }

    public String getHash() {
        String input = ((this.singer.trim() + this.title.trim() + this.duration).toUpperCase());
        return DigestUtils.md5Hex(input);
    }

}
