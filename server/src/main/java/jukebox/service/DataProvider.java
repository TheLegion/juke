package jukebox.service;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;

import java.io.InputStream;
import java.util.List;

public interface DataProvider {
    TrackSource getSourceType();
    List<Track> search(String query);

    InputStream download(Track track);
}
