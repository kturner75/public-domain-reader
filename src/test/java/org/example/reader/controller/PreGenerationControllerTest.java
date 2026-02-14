package org.example.reader.controller;

import org.example.reader.service.PreGenerationService;
import org.example.reader.service.PreGenerationService.PreGenResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreGenerationController.class)
@TestPropertySource(properties = {
        "generation.cache-only=false"
})
class PreGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreGenerationService preGenerationService;

    @Test
    void preGenerateForBook_success_returnsOk() throws Exception {
        PreGenResult result = new PreGenResult(
                true,
                "book-1",
                "Pride and Prejudice",
                "Generation complete",
                12,
                12,
                0,
                7,
                0,
                12,
                0,
                12,
                7,
                12
        );
        when(preGenerationService.preGenerateForBook("book-1")).thenReturn(result);

        mockMvc.perform(post("/api/pregen/book/book-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.bookId", is("book-1")))
                .andExpect(jsonPath("$.chaptersProcessed", is(12)))
                .andExpect(jsonPath("$.newRecaps", is(12)));
    }

    @Test
    void preGenerateByGutenbergId_failure_returnsBadRequest() throws Exception {
        when(preGenerationService.preGenerateByGutenbergId(1234))
                .thenReturn(PreGenResult.failure("Import failed: timed out"));

        mockMvc.perform(post("/api/pregen/gutenberg/1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Import failed: timed out")));
    }
}
