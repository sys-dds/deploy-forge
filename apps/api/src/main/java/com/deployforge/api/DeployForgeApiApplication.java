package com.deployforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DeployForgeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployForgeApiApplication.class, args);
    }
}
