package com.physiolink.exercise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
public class ExerciseApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExerciseApplication.class, args);
    }
}
