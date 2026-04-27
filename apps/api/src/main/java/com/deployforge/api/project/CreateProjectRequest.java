package com.deployforge.api.project;

public record CreateProjectRequest(
        String name,
        String slug,
        String description
) {
}
