package com.example.DataPreparationApp.Config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QUALIS DS - Data Processing Application API")
                        .description("""
                        QUALIS DS is a comprehensive data processing application with robust features for handling:
                        - Smart file uploads with chunked upload support for large files
                        - Real-time upload progress tracking and early validation
                        - Server-side performance monitoring via Micrometer and Prometheus
                        - Optimized database connection pooling with HikariCP
                        - Garbage collection and HTTP request metrics
                        - Client-side performance metrics dashboard with real-time updates
                        - Asynchronous file processing with robust error handling
                        
                        Technologies Used:
                        - Backend: Spring Boot, Micrometer, Prometheus, HikariCP, Apache Tika
                        - Frontend: Angular 15+, RxJS
                        
                        For detailed monitoring, access the metrics dashboard at /metrics.
                        """)
                        .version("1.0")
                        .termsOfService("https://mercure-it/terms")
                        .contact(new Contact()
                                .name("QUALIS DS")
                                .email("contact@mercureit.com")
                                .url("https://mercureit.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html"))
                );
    }
}
