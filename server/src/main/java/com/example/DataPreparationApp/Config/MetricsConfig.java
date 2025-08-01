package com.example.DataPreparationApp.Config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics including Garbage Collection monitoring
 */
@Configuration
public class MetricsConfig {

    /**
     * Configure JVM GC metrics to monitor garbage collection
     */
    @Bean
    public JvmGcMetrics gcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Monitor JVM memory usage
     */
    @Bean
    public JvmMemoryMetrics memoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Monitor thread utilization
     */
    @Bean
    public JvmThreadMetrics threadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * Monitor class loader metrics
     */
    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    /**
     * Monitor processor metrics
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Track application uptime
     */
    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }
} 