package jukebox.service;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;

import java.util.List;

public interface DataProvider {
    TrackSource getSourceType();
    List<Track> search(String query);
    byte[] download(Track track);
}
