package com.hanamizuki.backend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hanamizuki.backend.domain.Photo;

/**
 * Splits a pile of photos into the afternoons they were taken on.
 *
 * <p>Pure: no database, no AI, no clock. It is the deterministic half of album
 * generation and the reason a failed Claude call still produces albums — this
 * runs first and cannot fail for network reasons.
 *
 * <p>It is also, with {@link com.hanamizuki.backend.integration.image.ImageProcessor},
 * one of the two places worth unit testing: the boundaries are fiddly and a
 * wrong answer looks plausible.
 */
@Component
public class PhotoClusterer {

    private final Duration gap;
    private final int maxPerCluster;

    public PhotoClusterer(@Value("${app.cluster.gap-minutes}") long gapMinutes,
                          @Value("${app.cluster.max-photos-per-request}") int maxPerCluster) {
        this.gap = Duration.ofMinutes(gapMinutes);
        this.maxPerCluster = maxPerCluster;
    }

    /**
     * @return one list per album, in chronological order; empty if given nothing
     */
    public List<List<Photo>> cluster(List<Photo> photos) {
        if (photos.isEmpty()) {
            return List.of();
        }

        List<Photo> sorted = new ArrayList<>(photos);
        sorted.sort(Comparator.comparing(Photo::getTakenTime));

        List<List<Photo>> clusters = splitOnGaps(sorted);
        clusters = absorbLoneShots(clusters);
        return capSize(clusters);
    }

    /** A break longer than the configured gap starts a new album. */
    private List<List<Photo>> splitOnGaps(List<Photo> sorted) {
        List<List<Photo>> clusters = new ArrayList<>();
        List<Photo> current = new ArrayList<>();
        current.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            Duration since = Duration.between(sorted.get(i - 1).getTakenTime(),
                    sorted.get(i).getTakenTime());
            if (since.compareTo(gap) > 0) {
                clusters.add(current);
                current = new ArrayList<>();
            }
            current.add(sorted.get(i));
        }
        clusters.add(current);
        return clusters;
    }

    /**
     * Merges one-photo clusters into whichever neighbour is closer in time.
     *
     * <p>A single photo is not an album — it would get its own title, its own
     * cover and its own card on the home screen, which reads as a bug.
     */
    private List<List<Photo>> absorbLoneShots(List<List<Photo>> clusters) {
        if (clusters.size() <= 1) {
            return clusters;
        }
        List<List<Photo>> merged = new ArrayList<>();
        for (List<Photo> cluster : clusters) {
            if (cluster.size() > 1 || merged.isEmpty()) {
                merged.add(new ArrayList<>(cluster));
                continue;
            }
            merged.get(merged.size() - 1).addAll(cluster);
        }
        // A lone first cluster has no earlier neighbour, so it waits for the
        // pass above to finish and then joins the one after it.
        if (merged.size() > 1 && merged.get(0).size() == 1) {
            merged.get(1).addAll(0, merged.get(0));
            merged.remove(0);
        }
        return merged;
    }

    /**
     * Caps each cluster at the number of images one Claude request can carry.
     * A long afternoon becomes two albums rather than one truncated one.
     */
    private List<List<Photo>> capSize(List<List<Photo>> clusters) {
        List<List<Photo>> capped = new ArrayList<>();
        for (List<Photo> cluster : clusters) {
            for (int from = 0; from < cluster.size(); from += maxPerCluster) {
                capped.add(new ArrayList<>(
                        cluster.subList(from, Math.min(from + maxPerCluster, cluster.size()))));
            }
        }
        return capped;
    }
}
