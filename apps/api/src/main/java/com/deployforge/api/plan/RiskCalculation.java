package com.deployforge.api.plan;

import java.util.List;

public record RiskCalculation(
        RiskLevel riskLevel,
        List<String> riskReasons
) {
}
