package com.jelly.cinema.common.config;

import com.jelly.cinema.service.MediaService;
import com.jelly.cinema.service.TmdbSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MediaService mediaService;
    private final TmdbSyncService tmdbSyncService;

    @Override
    public void run(String... args) throws Exception {
        if (mediaService.count() == 0) {
            log.info("Database is empty. Populating initial data from TMDB...");
            tmdbSyncService.syncNowPlayingMovies();
            tmdbSyncService.syncPopularTvShows();
            log.info("Finished populating initial TMDB data.");
        } else {
            log.info("Media data already exists, skipping initial TMDB population.");
        }
    }
}
