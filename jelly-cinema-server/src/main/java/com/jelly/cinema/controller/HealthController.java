package com.jelly.cinema.controller;

import com.jelly.cinema.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public R<String> health() {
        return R.ok("Jelly Cinema Server is up and running!");
    }
}
