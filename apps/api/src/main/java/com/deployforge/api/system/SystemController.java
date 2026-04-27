package com.deployforge.api.system;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private static final String SERVICE_NAME = "deploy-forge-api";

    private final NodeProperties nodeProperties;

    public SystemController(NodeProperties nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("service", SERVICE_NAME, "status", "ok");
    }

    @GetMapping("/node")
    public Map<String, String> node() {
        return Map.of("service", SERVICE_NAME, "nodeId", nodeProperties.nodeId());
    }
}
