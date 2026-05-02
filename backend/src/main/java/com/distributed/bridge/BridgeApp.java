package com.distributed.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Map;

@SpringBootApplication(
        exclude = {DataSourceAutoConfiguration.class},
        scanBasePackages = "com.distributed.bridge"
)
public class BridgeApp {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BridgeApp.class);
        app.setDefaultProperties(Map.of("server.port", "8000"));
        app.run(args);
    }
}
