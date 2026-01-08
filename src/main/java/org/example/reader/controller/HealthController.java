package org.example.reader.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Health health() {
        return new Health("ok");
    }

    public record Health(String status) {}
}
