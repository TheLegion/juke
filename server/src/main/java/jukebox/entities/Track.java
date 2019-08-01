package jukebox.entities;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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
        LocalTime time = LocalTime.ofSecondOfDay(this.duration);
        String formattedTime = time.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String input = (this.singer.trim() + this.title.trim() + formattedTime).toUpperCase();
        try {
            return DigestUtils.md5Hex(input.getBytes("cp1251"));
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
