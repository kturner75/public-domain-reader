package org.example.reader.gutendex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GutendexResponse(
    int count,
    String next,
    String previous,
    List<GutendexBook> results
) {}
