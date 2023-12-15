package jukebox.service.providers;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;

import java.io.InputStream;
import java.util.List;

public interface DataProvider {
    String userAgent = "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)";
    TrackSource getSourceType();
    List<Track> search(String query);

    InputStream download(Track track);
}
