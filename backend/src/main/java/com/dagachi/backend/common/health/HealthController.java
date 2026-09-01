package com.dagachi.backend.common.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        return ResponseEntity.ok(
                Map.of("status", "UP")
        );
    }
}
