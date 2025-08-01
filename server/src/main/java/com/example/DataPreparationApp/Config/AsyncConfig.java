package com.example.DataPreparationApp.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // The Spring Boot auto-configuration will create a task executor based on
    // properties defined in application.properties
} 