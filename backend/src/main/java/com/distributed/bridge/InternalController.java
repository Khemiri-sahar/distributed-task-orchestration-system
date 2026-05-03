package com.distributed.bridge;

import com.distributed.grpc.NodeGrpcServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {

    // required=false so the bridge (which doesn't load NodeGrpcServer) can still start
    @Autowired(required = false)
    private NodeGrpcServer grpcServer;

    @PostMapping("/kill")
    public String kill() {
        if (grpcServer != null) grpcServer.killNode();
        return "killed";
    }

    @PostMapping("/revive")
    public String revive() {
        if (grpcServer != null) grpcServer.reviveNode();
        return "revived";
    }
}
