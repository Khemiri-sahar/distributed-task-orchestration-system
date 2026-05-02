package com.distributed.bridge;

import com.distributed.grpc.NodeGrpcServer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {

    @PostMapping("/kill")
    public String kill() {
        NodeGrpcServer.kill();
        return "killed";
    }

    @PostMapping("/revive")
    public String revive() {
        NodeGrpcServer.revive();
        return "revived";
    }
}
