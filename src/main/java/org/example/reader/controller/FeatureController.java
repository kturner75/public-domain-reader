package org.example.reader.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/features")
public class FeatureController {
    @Value("${speed-reading.enabled:true}")
    private boolean speedReadingEnabled;

    @GetMapping
    public Map<String, Object> getFeatures() {
        Map<String, Object> features = new HashMap<>();
        features.put("speedReadingEnabled", speedReadingEnabled);
        return features;
    }
}
