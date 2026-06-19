package com.arbook.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ArControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testResolveMarkerSuccess() throws Exception {
        mockMvc.perform(get("/api/ar-markers/resolve")
                .param("code", "BIO_CELL_001")
                .header("X-Unity-API-Key", "UnitySecretKey123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markerCode").value("BIO_CELL_001"))
                .andExpect(jsonPath("$.markerName").value("Marker cấu tạo tế bào"))
                .andExpect(jsonPath("$.modelName").value("Cell 3D Model"))
                .andExpect(jsonPath("$.labels").isArray())
                .andExpect(jsonPath("$.animationNames").isArray());
    }

    @Test
    public void testResolveMarkerNotFound() throws Exception {
        mockMvc.perform(get("/api/ar-markers/resolve")
                .param("code", "NON_EXISTING_CODE")
                .header("X-Unity-API-Key", "UnitySecretKey123")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testResolveMarkerUnauthorized() throws Exception {
        mockMvc.perform(get("/api/ar-markers/resolve")
                .param("code", "BIO_CELL_001")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
