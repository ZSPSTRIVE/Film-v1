package com.jelly.cinema.common.config;

import com.jelly.cinema.service.MediaService;
import com.jelly.cinema.service.TmdbSyncService;
import com.jelly.cinema.service.ai.rag.RagIndexingService;
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
    private final RagIndexingService ragIndexingService;

    @Override
    public void run(String... args) {
        boolean initialized = false;
        if (mediaService.count() == 0) {
            log.info("Database is empty. Populating initial data from TMDB...");
            tmdbSyncService.syncNowPlayingMovies();
            tmdbSyncService.syncPopularTvShows();
            initialized = true;
            log.info("Finished populating initial TMDB data.");
        } else {
            log.info("Media data already exists, skipping initial TMDB population.");
        }

        try {
            if (initialized) {
                log.info("Bootstrapping RAG knowledge base after initial media sync...");
            }
            ragIndexingService.bootstrapIfNeeded();
        } catch (Exception e) {
            log.warn("RAG bootstrap skipped because infrastructure is not ready.", e);
        }
    }
}
