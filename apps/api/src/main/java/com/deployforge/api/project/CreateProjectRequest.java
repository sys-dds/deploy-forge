package com.deployforge.api.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank
        @Size(max = 160)
        String name,
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$")
        String slug,
        String description,
        String ownerTeam,
        LifecycleStatus lifecycleStatus
) {
}
