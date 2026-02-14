package org.example.reader.model;

import java.time.LocalDateTime;

public record GenerationJobStatusResponse(
        String scope,
        String bookId,
        LocalDateTime asOf,
        GenerationPipelineStatus illustrations,
        GenerationPipelineStatus portraits,
        GenerationPipelineStatus analyses,
        GenerationPipelineStatus recaps,
        GenerationPipelineStatus totals
) {
}
