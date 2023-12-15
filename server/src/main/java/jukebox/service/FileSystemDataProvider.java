package jukebox.service;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import jukebox.entities.TrackState;
import jukebox.service.providers.DataProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import one.util.streamex.StreamEx;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileSystemDataProvider implements DataProvider {

    @Value("${cache.dir}")
    private String cacheDir;

    public TrackSource getSourceType() {
        return TrackSource.Cache;
    }

    public List<Track> search(String query) {
        try {
            Path hashmap = Paths.get(cacheDir, "hashmap.txt");

            if (!Files.exists(hashmap)) {
                return Collections.emptyList();
            }

            String[] queryWords = query.toUpperCase().split(" ");
            return StreamEx.of(Files.readAllLines(hashmap))
                           .filter(str -> !str.isEmpty())
                           .map(s -> getRelevance(s, queryWords))
                           .nonNull()
                           .sorted((f, s) -> -1 * Integer.compare(f.getRelevance(), s.getRelevance()))
                           .map(s -> this.getTrackByHash(s.getLine()))
                           .nonNull()
                           .toList();
        }
        catch (IOException e) {
            System.out.println("FileSystemDataProvider error, query " + query);
        }
        return Collections.emptyList();
    }

    public InputStream download(Track track) {
        throw new UnsupportedOperationException();
    }

    private Track getTrackByHash(String line) {
        Track track = new Track();

        String[] values = line.split("\\|");

        String hash = values[0];
        String singer = values[1];
        String title = values[2];
        String duration = values[3];
        LocalTime parsedDuration = LocalTime.parse(duration, DateTimeFormatter.ofPattern("HH:mm:ss"));

        if (!Files.exists(Paths.get(cacheDir, hash + ".mp3"))) {
            return null;
        }

        track.setId(hash);
        track.setSinger(singer);
        track.setTitle(title);
        track.setDuration(parsedDuration.toSecondOfDay());
        track.setSource(TrackSource.Cache);
        track.setState(TrackState.Ready);
        return track;
    }

    private boolean hasExactWord(String line, String word) {
        return exactWordIndex(line, word) >= 0;
    }

    private int exactWordIndex(String line, String word) {
        int indexOfWord = line.indexOf(word);
        if (indexOfWord == -1) {
            return -1;
        }
        int startPos = indexOfWord + word.length();

        if (word.isEmpty()) {
            return 0;
        }

        while (true) {
            boolean hasSpaceBefore = false;
            boolean hasSpaceAfter = false;

            if (indexOfWord == 0) {
                hasSpaceBefore = true;
            }
            if ((indexOfWord + word.length()) == line.length()) {
                hasSpaceAfter = true;
            }
            if ((indexOfWord - 1) >= 0 &&
                    !Character.isLetterOrDigit(line.charAt(indexOfWord - 1))) {
                hasSpaceBefore = true;
            }
            if ((indexOfWord + word.length()) < (line.length() - 1) &&
                    !Character.isLetterOrDigit(line.charAt(indexOfWord + word.length()))) {
                hasSpaceAfter = true;
            }

            if (hasSpaceBefore && hasSpaceAfter) {
                return indexOfWord;
            }

            indexOfWord = line.indexOf(word, startPos);
            if (indexOfWord == -1) {
                break;
            }
            startPos = indexOfWord + word.length();
        }
        return -1;
    }

    private SearchResult getRelevance(String line, String[] queryWords) {
        String tempLine = Arrays.stream(line.toUpperCase().split("\\|"))
                                .skip(1)
                                .limit(2)
                                .collect(Collectors.joining(" "));

        boolean hasAllQueryWords = true;
        int relevance = 0;
        int prevIndex = -1;
        int currentIndex;
        for (String word : queryWords) {
            if (tempLine.contains(word)) {
                currentIndex = tempLine.indexOf(word);
                if (hasExactWord(tempLine, word)) {
                    currentIndex = exactWordIndex(tempLine, word);
                    relevance += 3;
                } else {
                    relevance++;
                }

                if (prevIndex != -1) {
                    if (currentIndex > prevIndex) {
                        relevance++;
                    }
                }
                prevIndex = currentIndex;
            } else {
                hasAllQueryWords = false;
                break;
            }
        }
        if (hasAllQueryWords) {
            return new SearchResult(line, relevance);
        } else {
            return null;
        }
    }

    @Getter
    @AllArgsConstructor
    private static class SearchResult {
        private String line;
        private int relevance;
    }
}
