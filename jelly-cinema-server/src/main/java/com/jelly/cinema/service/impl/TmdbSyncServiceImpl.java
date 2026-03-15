package com.jelly.cinema.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jelly.cinema.common.config.property.TmdbProperties;
import com.jelly.cinema.model.entity.Media;
import com.jelly.cinema.service.MediaService;
import com.jelly.cinema.service.TmdbSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbSyncServiceImpl implements TmdbSyncService {

    private final TmdbProperties tmdbProperties;
    private final MediaService mediaService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void syncNowPlayingMovies() {
        String url = tmdbProperties.getBaseUrl() + "/movie/now_playing?api_key=" + tmdbProperties.getApiKey() + "&language=zh-CN&page=1";
        try {
            String response = restTemplate.getForObject(url, String.class);
            JSONObject json = JSON.parseObject(response);
            JSONArray results = json.getJSONArray("results");
            
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                Media media = new Media();
                media.setTitle(item.getString("title"));
                media.setOriginalTitle(item.getString("original_title"));
                media.setType(1); // Movie
                media.setStatus(2); // Showing
                
                String releaseDateStr = item.getString("release_date");
                if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
                    media.setReleaseDate(LocalDate.parse(releaseDateStr));
                }
                
                String posterPath = item.getString("poster_path");
                if (posterPath != null) {
                    media.setCoverUrl(tmdbProperties.getImageBaseUrl() + posterPath);
                }
                
                String backdropPath = item.getString("backdrop_path");
                if (backdropPath != null) {
                    media.setBackdropUrl(tmdbProperties.getOriginalImageBaseUrl() + backdropPath);
                }
                
                media.setSummary(item.getString("overview"));
                media.setRating(BigDecimal.valueOf(item.getDoubleValue("vote_average")));
                
                mediaService.save(media);
            }
            log.info("Successfully synced {} movies from TMDB", results.size());
        } catch (Exception e) {
            log.error("Failed to sync TMDB movies: {}", e.getMessage());
        }
    }

    @Override
    public void syncPopularTvShows() {
        String url = tmdbProperties.getBaseUrl() + "/tv/popular?api_key=" + tmdbProperties.getApiKey() + "&language=zh-CN&page=1";
        try {
            String response = restTemplate.getForObject(url, String.class);
            JSONObject json = JSON.parseObject(response);
            JSONArray results = json.getJSONArray("results");
            
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                Media media = new Media();
                media.setTitle(item.getString("name"));
                media.setOriginalTitle(item.getString("original_name"));
                media.setType(2); // TV
                media.setStatus(2); // Showing
                
                String airDateStr = item.getString("first_air_date");
                if (airDateStr != null && !airDateStr.isEmpty()) {
                    media.setReleaseDate(LocalDate.parse(airDateStr));
                }
                
                String posterPath = item.getString("poster_path");
                if (posterPath != null) {
                    media.setCoverUrl(tmdbProperties.getImageBaseUrl() + posterPath);
                }
                
                String backdropPath = item.getString("backdrop_path");
                if (backdropPath != null) {
                    media.setBackdropUrl(tmdbProperties.getOriginalImageBaseUrl() + backdropPath);
                }
                
                media.setSummary(item.getString("overview"));
                media.setRating(BigDecimal.valueOf(item.getDoubleValue("vote_average")));
                
                mediaService.save(media);
            }
            log.info("Successfully synced {} TV shows from TMDB", results.size());
        } catch (Exception e) {
            log.error("Failed to sync TMDB TV shows: {}", e.getMessage());
        }
    }
}
