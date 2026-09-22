package com.flowerconnect;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.CorsProperties;
import com.flowerconnect.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, AppProperties.class})
public class FlowerConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowerConnectApplication.class, args);
    }
}