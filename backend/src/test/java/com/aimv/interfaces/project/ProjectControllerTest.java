package com.aimv.interfaces.project;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsRecentProjectsForSidebarHistory() throws Exception {
        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"都市悬疑\",\"goal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"赛博侦探\",\"goal\":\"生成一张赛博侦探短剧封面\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].title").value("赛博侦探"))
            .andExpect(jsonPath("$.data[0].goal").value("生成一张赛博侦探短剧封面"))
            .andExpect(jsonPath("$.data[1].title").value("都市悬疑"));
    }
}
