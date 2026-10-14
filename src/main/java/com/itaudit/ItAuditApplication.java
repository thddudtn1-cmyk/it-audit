package com.itaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ItAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItAuditApplication.class, args);
    }
}
