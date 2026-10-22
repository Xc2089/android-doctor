package com.androiddoctor;

import com.androiddoctor.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AndroidDoctorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AndroidDoctorApplication.class, args);
    }
}

