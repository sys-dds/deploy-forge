package com.deployforge.api.system;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deployforge.node")
public record NodeProperties(String nodeId) {
}
