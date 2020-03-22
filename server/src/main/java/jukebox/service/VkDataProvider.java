package jukebox.service;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import one.util.streamex.StreamEx;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;

@Service
public class VkDataProvider implements DataProvider {

    private static long cookieTimeout = 4 * 60 * 60 * 1000;
    private static String userAgent = "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1)";

    @Value("${vk.login}")
    private String vkLogin;

    @Value("${vk.password}")
    private String vkPassword;

    @Value("${vk.id}")
    private String vkId;

    @Value("${download.timeout}")
    private String downloadTimeout;

    private String cookie = null;
    private long cookieUseTime = 0;

    private static Track getTrackByElement(Element divTrack) {
        try {
            Track track = new Track();

            Elements nodeWithUrl = divTrack.getElementsByTag("input");
            if (nodeWithUrl.first() == null || StringUtils.isEmpty(nodeWithUrl.attr("value"))) {
                return null;
            }

            track.setUri(new URI(nodeWithUrl.attr("value")));
            track.setTitle(getFirstNodeText(divTrack, "ai_title"));
            track.setSinger(getFirstNodeText(divTrack, "ai_artist"));
            Elements durationNode = divTrack.getElementsByClass("ai_dur");
            track.setDuration(Long.parseLong(durationNode.attr("data-dur")));
            track.setSource(TrackSource.VK);
            track.setId(track.getHash());

            return track;
        }
        catch (Exception e) {
            System.out.println("VKComDataProvider (track parse): ");
            e.printStackTrace();
        }
        return null;
    }

    private static String getFirstNodeText(Element parent, String cssClass) {
        return prepareDataLine(parent.getElementsByClass(cssClass).first().text());
    }

    private static String prepareDataLine(String content) {
        return content.replaceAll("<em class=\"found\">", "")
                      .replaceAll("</em>", "")
                      .trim();
    }

    public TrackSource getSourceType() {
        return TrackSource.VK;
    }

    public List<Track> search(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            boolean cookieExists = cookie != null && cookie.isEmpty();
            boolean cookieExpired = cookieUseTime - System.currentTimeMillis() > cookieTimeout;
            if (!cookieExists || cookieExpired) {
                cookie = authenticate(vkLogin, vkPassword);
                if (cookie == null || cookie.isEmpty()) {
                    throw new Exception("Failed to authorize at vk.com.");
                }
            }

            cookieUseTime = System.currentTimeMillis();
            String url = "https://m.vk.com/search";
            Document doc = Jsoup.connect(url)
                                .header("content-type", "application/x-www-form-urlencoded")
                                .userAgent(userAgent)
                                .cookie("remixsid", cookie)
                                .data("al", "1")
                                .data("c[q]", query)
                                .data("c[section]", "audio")
                                .post();

            if (doc == null) {
                return Collections.emptyList();
            }
            Elements elements = doc.getElementsByClass("ai_body");

            return StreamEx.of(elements)
                           .map(VkDataProvider::getTrackByElement)
                           .nonNull()
                           .toList();
        }
        catch (Exception e) {
            System.out.println("VKComDataProvider (global): " + e.getMessage());
            System.out.println("Query: " + query);
        }
        return Collections.emptyList();
    }

    public InputStream download(Track track) {
        try {
            String trackUrl = track.getUri().toString();
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("javascript");
            String decodedTrackUrl = engine.eval(getRevealScript() + "s('" + trackUrl + "')").toString();

            URLConnection connection = new URL(decodedTrackUrl).openConnection();
            connection.setConnectTimeout(Integer.parseInt(downloadTimeout));
            return connection.getInputStream();
        }
        catch (Exception e) {
            System.out.println("VKComDataProvider error: " + e.getMessage());
        }
        return null;
    }

    private String getRevealScript() {
        try (InputStream input = VkDataProvider.class.getResourceAsStream("/reveal-vk-audio-url.js")) {
            return IOUtils.toString(input, Charsets.UTF_8).replaceAll("\\$\\{vk\\.id}", vkId);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String authenticate(String email, String pass) throws IOException {
        String defaultURL = "https://m.vk.com";
        int timeout = 100000;
        Connection.Response execute = Jsoup.connect(defaultURL)
                                           .userAgent(userAgent)
                                           .timeout(timeout)
                                           .execute();
        Document doc = execute.parse();
        Element formElement = doc.getElementsByTag("form").first();
        String authUrl = formElement.attr("action");

        String sid = Jsoup.connect(authUrl)
                          .userAgent(userAgent)
                          .timeout(timeout)
                          .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                          )
                          .header("accept-language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                          .header("accept-encoding", "gzip, deflate")
                          .header("connection", "keep-alive")
                          .header("content-type", "application/x-www-form-urlencoded")
                          .data("email", email)
                          .data("pass", pass)
                          .cookies(execute.cookies())
                          .method(Connection.Method.POST)
                          .execute()
                          .cookies()
                          .get("remixsid");
        if ("".equals(sid)) {
            return null;
        } else {
            return sid;
        }
    }

}
