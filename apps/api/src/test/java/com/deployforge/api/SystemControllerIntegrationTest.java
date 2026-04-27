package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SystemControllerIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsServiceStatus() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("deploy-forge-api"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void nodeReturnsDefaultNodeId() throws Exception {
        mockMvc.perform(get("/api/v1/system/node"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("deploy-forge-api"))
                .andExpect(jsonPath("$.nodeId").value("local-node-1"));
    }
}
