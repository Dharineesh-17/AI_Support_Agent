package com.example.aiagent.config;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC and async configuration.
 *
 * <p>Configures:
 * <ul>
 *   <li>CORS policy — permissive for local development (restrict in production)</li>
 *   <li>Async request timeout for SSE emitters</li>
 *   <li>Virtual Thread executor for {@link org.springframework.scheduling.annotation.Async} methods</li>
 *   <li>Static resource handler for the frontend SPA</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class WebConfig implements WebMvcConfigurer, AsyncConfigurer {

    /**
     * CORS filter — allows all origins in development.
     * In production, replace {@code "*"} with specific allowed origins.
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of("*"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setExposedHeaders(List.of("Location", "Content-Type", "X-Request-Id"));
        corsConfig.setAllowCredentials(false);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfig);

        return new CorsFilter(source);
    }

    /**
     * Configures MVC async support with a generous timeout for SSE streams.
     * Virtual threads handle the actual blocking I/O efficiently.
     */
    @Override
public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    org.springframework.core.task.SimpleAsyncTaskExecutor executor = 
        new org.springframework.core.task.SimpleAsyncTaskExecutor();
    // Enable Java 21 Virtual Threads natively on the asynchronous executor engine
    executor.setVirtualThreads(true);
    configurer.setTaskExecutor(executor);
}


    /**
     * Virtual-thread-backed async executor for {@code @Async} annotated methods.
     *
     * <p>Each submitted task runs on its own lightweight virtual thread,
     * allowing thousands of concurrent SSE connections without platform thread exhaustion.</p>
     */
    @Bean(name = "virtualThreadTaskExecutor")
    public org.springframework.core.task.TaskExecutor virtualThreadTaskExecutor() {
        return task -> Thread.ofVirtual()
                             .name("async-vt-", 0)
                             .start(task);
    }

    /**
     * {@link AsyncConfigurer} override — provides the virtual thread executor
     * for {@code @Async} annotated methods throughout the application.
     */
    @Override
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Static resource handler for the frontend SPA served from
     * {@code src/main/resources/static/}.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }
}
