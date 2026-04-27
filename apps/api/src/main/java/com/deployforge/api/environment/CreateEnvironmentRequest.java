package com.deployforge.api.environment;

import com.deployforge.api.project.LifecycleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank
        @Size(max = 160)
        String name,
        String environmentType,
        boolean protectedEnvironment,
        @Min(1)
        int sortOrder,
        String externalTargetId,
        boolean requiresApproval,
        LifecycleStatus lifecycleStatus
) {
}
