package jukebox.service;

import jukebox.entities.Track;
import jukebox.entities.TrackSource;
import jukebox.entities.TrackState;
import one.util.streamex.StreamEx;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

            List<String> lines = Files.readAllLines(hashmap);
            Collections.reverse(lines);
            Map<String, Integer> mostPossibleResults = StreamEx.of(lines)
                                                               .toMap(s -> s, s -> getRelevance(s, queryWords));

            return StreamEx.of(mostPossibleResults.entrySet())
                           .filter(entry -> entry.getValue() > 0)
                           .sorted((f, s) -> -1 * Integer.compare(f.getValue(), s.getValue()))
                           .map(this::getTrackByHash)
                           .nonNull()
                           .toList();
        }
        catch (IOException e) {
            System.out.println("FileSystemDataProvider error, query " + query);
        }
        return Collections.emptyList();
    }

    private Track getTrackByHash(Map.Entry<String, Integer> entry) {
        Track track = new Track();

        String[] values = entry.getKey().split("\\|");

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

    public byte[] download(Track track) {
        throw new UnsupportedOperationException();
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

        if (word.equals("")) {
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

    private Integer getRelevance(String line, String[] queryWords) {
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
            return relevance;
        } else {
            return 0;
        }
    }
}
