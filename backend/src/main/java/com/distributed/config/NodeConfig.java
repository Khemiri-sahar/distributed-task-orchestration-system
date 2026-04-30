package com.distributed.config;

import com.distributed.state.NodeState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class NodeConfig {

    @Bean
    public NodeState nodeState(
            @Value("${node.id}") int nodeId,
            @Value("${node.peers}") String peersRaw) {
        List<String> peers = Arrays.asList(peersRaw.split(","));
        return new NodeState(nodeId, peers);
    }
}
