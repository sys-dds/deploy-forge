package com.deployforge.api.service;

public record CreateServiceRequest(
        String name,
        String slug,
        String repositoryUrl
) {
}
