package com.queuewise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class QueueWiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueWiseApplication.class, args);
    }
}
