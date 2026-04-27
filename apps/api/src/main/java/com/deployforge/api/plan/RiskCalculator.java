package com.deployforge.api.plan;

import java.util.ArrayList;

import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.environment.EnvironmentType;
import com.deployforge.api.service.ServiceResponse;
import com.deployforge.api.service.ServiceTier;
import org.springframework.stereotype.Component;

@Component
public class RiskCalculator {

    public RiskCalculation calculate(EnvironmentResponse environment, ServiceResponse service, DeploymentStrategy strategy) {
        ArrayList<String> reasons = new ArrayList<>();
        RiskLevel level = RiskLevel.LOW;
        if (environment.environmentType() == EnvironmentType.QA || environment.environmentType() == EnvironmentType.STAGING) {
            level = max(level, RiskLevel.MEDIUM);
            reasons.add("TARGET_ENVIRONMENT_NON_DEV");
        }
        if (strategy == DeploymentStrategy.CANARY) {
            level = max(level, RiskLevel.MEDIUM);
            reasons.add("CANARY_STRATEGY");
        }
        if (environment.protectedEnvironment()) {
            level = max(level, RiskLevel.HIGH);
            reasons.add("TARGET_ENVIRONMENT_PROTECTED");
        }
        if (environment.environmentType() == EnvironmentType.PROD) {
            level = max(level, RiskLevel.HIGH);
            reasons.add("TARGET_ENVIRONMENT_PROD");
        }
        if (environment.environmentType() == EnvironmentType.PROD && service.serviceTier() == ServiceTier.CRITICAL) {
            level = RiskLevel.CRITICAL;
            reasons.add("CRITICAL_SERVICE_TO_PROD");
        }
        if (reasons.isEmpty()) {
            reasons.add("STANDARD_DEV_DEPLOYMENT");
        }
        return new RiskCalculation(level, reasons);
    }

    private RiskLevel max(RiskLevel current, RiskLevel candidate) {
        return candidate.ordinal() > current.ordinal() ? candidate : current;
    }
}
