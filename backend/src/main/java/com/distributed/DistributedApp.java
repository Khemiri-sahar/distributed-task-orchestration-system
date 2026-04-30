package com.distributed;

import com.distributed.grpc.NodeGrpcServer;
import com.distributed.consensus.ConsensusManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class DistributedApp {

    @Autowired
    private NodeGrpcServer grpcServer;

    @Autowired
    private ConsensusManager consensusManager;

    @Value("${node.grpc.port}")
    private int grpcPort;

    public static void main(String[] args) {
        SpringApplication.run(DistributedApp.class, args);
    }

    @PostConstruct
    public void init() throws IOException {
        grpcServer.start(grpcPort);
        Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> consensusManager.start(), 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        grpcServer.stop();
    }
}
