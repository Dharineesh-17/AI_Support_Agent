package com.example.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the AI-Powered Autonomous Customer Support Agent.
 *
 * <p>Java 21 Virtual Threads are enabled via {@code spring.threads.virtual.enabled=true}
 * in application.yml, which automatically configures Tomcat's thread pool to use
 * Project Loom virtual threads — providing near-unlimited I/O concurrency with
 * minimal overhead per request.</p>
 */
@SpringBootApplication
@EnableAsync
public class AiCustomerAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCustomerAgentApplication.class, args);
    }
}
