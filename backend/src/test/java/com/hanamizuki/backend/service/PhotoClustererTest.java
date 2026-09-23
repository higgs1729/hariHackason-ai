package com.hanamizuki.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hanamizuki.backend.domain.Photo;

/**
 * The boundaries here decide how many albums a user gets, and a wrong answer
 * looks entirely plausible — six photos becoming one album instead of two is
 * not something you notice without checking.
 */
class PhotoClustererTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 9, 20, 12, 0);

    /** 30-minute gap, 12 photos per cluster: the configured production values. */
    private final PhotoClusterer clusterer = new PhotoClusterer(30, 12);

    private List<Photo> at(long... minuteOffsets) {
        List<Photo> photos = new ArrayList<>();
        for (long offset : minuteOffsets) {
            Photo photo = new Photo();
            photo.setTakenTime(NOON.plusMinutes(offset));
            photos.add(photo);
        }
        return photos;
    }

    @Test
    void returnsNothingForNoPhotos() {
        assertThat(clusterer.cluster(List.of())).isEmpty();
    }

    @Test
    void keepsPhotosTogetherWhenTheGapIsExactlyTheThreshold() {
        // 30 minutes is "still the same afternoon"; only longer splits.
        assertThat(clusterer.cluster(at(0, 30, 60))).hasSize(1);
    }

    @Test
    void splitsWhenTheGapExceedsTheThreshold() {
        List<List<Photo>> clusters = clusterer.cluster(at(0, 5, 36, 40));
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).hasSize(2);
        assertThat(clusters.get(1)).hasSize(2);
    }

    @Test
    void sortsBeforeClustering() {
        // Upload order says nothing about capture order.
        List<List<Photo>> clusters = clusterer.cluster(at(40, 0, 36, 5));
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0).get(0).getTakenTime()).isEqualTo(NOON);
    }

    @Test
    void mergesAStragglerIntoTheClusterBeforeIt() {
        // One photo three hours later is not its own album.
        List<List<Photo>> clusters = clusterer.cluster(at(0, 5, 10, 180));
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).hasSize(4);
    }

    @Test
    void mergesALeadingStragglerForwards() {
        // The first cluster has nothing before it, so it joins what follows.
        List<List<Photo>> clusters = clusterer.cluster(at(0, 180, 185, 190));
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0)).hasSize(4);
        assertThat(clusters.get(0).get(0).getTakenTime()).isEqualTo(NOON);
    }

    @Test
    void keepsASinglePhotoWhenThereIsNothingToMergeItWith() {
        assertThat(clusterer.cluster(at(0))).hasSize(1);
    }

    @Test
    void splitsAClusterThatExceedsOneRequestsWorthOfImages() {
        long[] thirteenMinutesApart = new long[13];
        for (int i = 0; i < 13; i++) {
            thirteenMinutesApart[i] = i;
        }
        List<List<Photo>> clusters = clusterer.cluster(at(thirteenMinutesApart));
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).hasSize(12);
        assertThat(clusters.get(1)).hasSize(1);
    }

    @Test
    void producesTwoAlbumsForTwoAfternoons() {
        // The shape the seed data and the demo actually use.
        List<Photo> twoDays = at(0, 7, 14, 21, 28, 35);
        twoDays.addAll(at(1440, 1447, 1454, 1461));
        List<List<Photo>> clusters = clusterer.cluster(twoDays);
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0)).hasSize(6);
        assertThat(clusters.get(1)).hasSize(4);
    }
}
