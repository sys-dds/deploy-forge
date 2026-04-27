package com.deployforge.api.artifact;

import java.util.List;
import java.util.UUID;

public record ArtifactDeployabilityResponse(
        UUID artifactId,
        UUID environmentId,
        boolean deployable,
        List<DeployabilityCheckResponse> checks
) {
}
