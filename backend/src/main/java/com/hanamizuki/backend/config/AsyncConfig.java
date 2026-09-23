package com.hanamizuki.backend.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Album generation runs off the request thread, because a Claude call per
 * cluster takes tens of seconds and the client polls instead of waiting.
 *
 * <p>Two threads, not a cached pool: every task in it spends money, and the
 * per-user concurrency check already limits demand to roughly one at a time.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("generationExecutor")
    public Executor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("generate-");
        executor.initialize();
        return executor;
    }
}
