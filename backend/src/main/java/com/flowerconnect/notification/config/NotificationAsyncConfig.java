package com.flowerconnect.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor for notification listeners (plan Phase 9, task 9.1).
 *
 * <p>Bounded core (2) / max (4) threads with a bounded queue (500). Rejection
 * is non-blocking: a rejected notification task is logged and discarded, it is
 * never run on the publishing (business) thread. Under sustained listener load
 * beyond queue capacity some notifications may be dropped -- the accepted
 * latency-versus-delivery tradeoff, since notifications must never affect
 * business flows.
 */
@Slf4j
@Configuration
public class NotificationAsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-executor-");
        executor.setRejectedExecutionHandler(discardWithLogging());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler discardWithLogging() {
        return (r, executor) -> log.warn(
                "Notification executor queue full (core={}, max={}, queue={}); " +
                        "dropping notification delivery task",
                executor.getCorePoolSize(), executor.getMaximumPoolSize(),
                executor.getQueue().remainingCapacity());
    }
}
