package com.example.hot6novelcraft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
@EnableRetry
//@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class Hot6NovelCraftApplication {

    public static void main(String[] args) {
        SpringApplication.run(Hot6NovelCraftApplication.class, args);
    }

}
