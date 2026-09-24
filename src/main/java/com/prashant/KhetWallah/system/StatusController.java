package com.prashant.KhetWallah.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, String> getStatus() {
        return Map.of(
                "application", "KhetWallah",
                "message", "Connecting farmers and buyers"
        );
    }
}