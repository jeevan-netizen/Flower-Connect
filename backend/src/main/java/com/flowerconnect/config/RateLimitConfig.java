package com.flowerconnect.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public Cache<String, Bucket> bucketCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(2))
                .maximumSize(10_000)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = false)
    public RateLimitFilter rateLimitFilter(Cache<String, Bucket> bucketCache,
                                           RateLimitProperties rateLimitProperties,
                                           Clock clock) {
        return new RateLimitFilter(bucketCache, rateLimitProperties, clock);
    }

    public static String buildKey(String endpoint, String hashedEmail, String ip) {
        return endpoint + ":" + hashedEmail + ":" + ip;
    }

    public static Bucket createBucket(int limit, Clock clock) {
        TimeMeter timeMeter = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                // Use the injected clock's instant to compute nanos since epoch
                // This aligns bucket refill with the application's time source
                return clock.instant().getEpochSecond() * 1_000_000_000L
                        + clock.instant().getNano();
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }

            @Override
            public String toString() {
                return "ClockBasedTimeMeter[" + clock + "]";
            }
        };

        return new LocalBucketBuilder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofHours(1))))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}