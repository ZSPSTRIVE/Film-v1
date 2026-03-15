package com.jelly.cinema.service;

public interface TmdbSyncService {
    void syncNowPlayingMovies();
    void syncPopularTvShows();
}
