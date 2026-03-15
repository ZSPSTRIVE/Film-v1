package com.jelly.cinema.controller;

import com.jelly.cinema.common.result.R;
import com.jelly.cinema.service.TmdbSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
public class TmdbSyncController {

    private final TmdbSyncService tmdbSyncService;

    @PostMapping("/tmdb/movies")
    public R<Void> syncMovies() {
        tmdbSyncService.syncNowPlayingMovies();
        return R.ok();
    }

    @PostMapping("/tmdb/tv")
    public R<Void> syncTvShows() {
        tmdbSyncService.syncPopularTvShows();
        return R.ok();
    }
}
