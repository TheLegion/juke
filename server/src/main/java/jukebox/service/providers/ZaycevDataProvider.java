package jukebox.service.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import one.util.streamex.StreamEx;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ZaycevDataProvider implements DataProvider {

    @Value("${download.timeout}")
    private String downloadTimeout;

    private static Track getTrack(Element element) {
        try {
            Track track = new Track();

            track.setSinger(getFirstChildText(element, "musicset-track__artist").trim());
            track.setTitle(getFirstChildText(element, "musicset-track__track-name").trim());
            Long duration = Optional.ofNullable(element.attributes().dataset().get("duration"))
                                    .map(Long::parseLong)
                                    .orElse(0L);
            track.setDuration(duration);
            track.setUri(new URI("https://zaycev.net" + element.attributes().dataset().get("url")));
            track.setSource(TrackSource.Zaycev);
            track.setId(track.getHash());

            return track;
        }
        catch (Exception e) {
            System.out.println("ZaycevDataProvider track parse error: ");
            e.printStackTrace();
        }
        return null;
    }

    private static String getFirstChildText(Element parent, String cssClass) {
        return parent.getElementsByClass(cssClass).first().text();
    }

    @Override
    public TrackSource getSourceType() {
        return TrackSource.Zaycev;
    }

    @Override
    public List<Track> search(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String url = "https://zaycev.net/search.html?query_search=" + query;
            Document doc = Jsoup.connect(url)
                                .userAgent(userAgent)
                                .get();

            if (doc == null) {
                return Collections.emptyList();
            }
            return StreamEx.of(doc.getElementsByClass("musicset-track"))
                           .map(ZaycevDataProvider::getTrack)
                           .nonNull()
                           .toList();
        }
        catch (Exception e) {
            System.out.println("ZaycevDataProvider error, query: " + query);
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    @Override
    public InputStream download(Track track) {
        try {
            int timeout = Integer.parseInt(downloadTimeout);
            String body = Jsoup.connect(track.getUri().toString())
                               .userAgent(userAgent)
                               .timeout(timeout)
                               .followRedirects(false)
                               .ignoreContentType(true)
                               .execute()
                               .body();
            String location = (String) new ObjectMapper().readValue(body, Map.class).get("url");
            URLConnection connection = new URL(location).openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            return connection.getInputStream();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
