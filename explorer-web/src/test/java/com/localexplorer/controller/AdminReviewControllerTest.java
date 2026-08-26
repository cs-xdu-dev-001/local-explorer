package com.localexplorer.controller;

import com.localexplorer.controller.admin.ReviewController;
import com.localexplorer.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        ReviewController reviewController = new ReviewController();
        ReflectionTestUtils.setField(reviewController, "reviewService", reviewService);
        mockMvc = MockMvcBuilders.standaloneSetup(reviewController).build();
    }

    @Test
    void replyEndpointCallsService() throws Exception {
        mockMvc.perform(put("/admin/review/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":51,\"replyContent\":\"Thanks\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(reviewService).reply(51L, "Thanks");
    }
}
