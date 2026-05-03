package com.distributed.grpc;

import com.distributed.consensus.ConsensusManager;
import com.distributed.executor.TaskExecutor;
import com.distributed.executor.TaskLogger;
import com.distributed.grpc.proto.NodeProto;
import com.distributed.grpc.proto.NodeServiceGrpc;
import com.distributed.model.NodeRole;
import com.distributed.model.TaskLog;
import com.distributed.mutex.LamportMutex;
import com.distributed.state.NodeState;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NodeGrpcServer extends NodeServiceGrpc.NodeServiceImplBase {

    private final NodeState state;
    private final ConsensusManager consensusManager;
    private final LamportMutex lamportMutex;
    private final TaskExecutor taskExecutor;
    private final TaskLogger taskLogger;

    private Server grpcServer;
    private static final AtomicBoolean isKilled = new AtomicBoolean(false);

    public NodeGrpcServer(NodeState state, ConsensusManager consensusManager,
                          LamportMutex lamportMutex,
                          @Qualifier("nodeTaskExecutor") TaskExecutor taskExecutor,
                          TaskLogger taskLogger) {
        this.state = state;
        this.consensusManager = consensusManager;
        this.lamportMutex = lamportMutex;
        this.taskExecutor = taskExecutor;
        this.taskLogger = taskLogger;
    }

    public void start(int port) throws IOException {
        grpcServer = ServerBuilder.forPort(port)
                .addService(this)
                .executor(Executors.newFixedThreadPool(10))
                .build()
                .start();
        System.out.println("gRPC server started on port " + port);
    }

    public void stop() {
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }

    // Instance methods used by InternalController — also reset node state
    public void killNode() {
        isKilled.set(true);
        state.setRole(NodeRole.FOLLOWER);
        state.setCurrentLeader(-1);
        System.out.println("Node " + state.getNodeId() + " killed");
    }

    public void reviveNode() {
        isKilled.set(false);
        // Reset heartbeat timestamp to zero so the watchdog fires and re-integrates
        state.setLastHeartbeatMs(0);
        System.out.println("Node " + state.getNodeId() + " revived — waiting for leader heartbeat or will trigger election");
    }

    public static boolean isKilled() { return isKilled.get(); }

    @Override
    public void submitTask(NodeProto.TaskRequest request,
                           StreamObserver<NodeProto.TaskResponse> responseObserver) {
        if (isKilled.get()) {
            responseObserver.onError(Status.UNAVAILABLE.asException());
            return;
        }

        if (state.getRole() != NodeRole.LEADER) {
            try {
                NodeServiceGrpc.NodeServiceBlockingStub leaderStub = consensusManager.getLeaderStub();
                if (leaderStub == null) {
                    responseObserver.onNext(NodeProto.TaskResponse.newBuilder()
                            .setSuccess(false).setError("No leader available").build());
                    responseObserver.onCompleted();
                    return;
                }
                NodeProto.TaskResponse forwarded = leaderStub
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .submitTask(request);
                responseObserver.onNext(forwarded.toBuilder().setRedirected(true).build());
            } catch (StatusRuntimeException e) {
                responseObserver.onNext(NodeProto.TaskResponse.newBuilder()
                        .setSuccess(false).setError("Leader unreachable").build());
            } finally {
                responseObserver.onCompleted();
            }
            return;
        }

        // LEADER path
        try {
            lamportMutex.acquireLock(request.getTaskId());
            TaskLog log = taskExecutor.execute(request, state.getNodeId(), state);
            taskLogger.writeLog(log, state.getNodeId());
            NodeProto.TaskResponse response = NodeProto.TaskResponse.newBuilder()
                    .setSuccess(log.getError() == null || log.getError().isEmpty())
                    .setResult(log.getResult() != null ? log.getResult() : "")
                    .setExecutedBy(state.getNodeId())
                    .setRedirected(false)
                    .setError(log.getError() != null ? log.getError() : "")
                    .build();
            responseObserver.onNext(response);
        } catch (Exception e) {
            responseObserver.onNext(NodeProto.TaskResponse.newBuilder()
                    .setSuccess(false).setError(e.getMessage()).build());
        } finally {
            lamportMutex.releaseLock(request.getTaskId());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void sendHeartbeat(NodeProto.HeartbeatRequest request,
                              StreamObserver<NodeProto.HeartbeatResponse> responseObserver) {
        if (isKilled.get()) {
            responseObserver.onError(Status.UNAVAILABLE.asException());
            return;
        }
        state.updateLamportClock(0);
        consensusManager.onHeartbeatReceived(request.getLeaderId(), request.getTerm());
        responseObserver.onNext(NodeProto.HeartbeatResponse.newBuilder()
                .setAcknowledged(true)
                .setNodeId(state.getNodeId())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void acquireLock(NodeProto.LockRequest request,
                            StreamObserver<NodeProto.LockResponse> responseObserver) {
        if (isKilled.get()) {
            responseObserver.onError(Status.UNAVAILABLE.asException());
            return;
        }
        NodeProto.LockResponse resp = lamportMutex.onLockRequested(request);
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void releaseLock(NodeProto.LockRequest request,
                            StreamObserver<NodeProto.LockResponse> responseObserver) {
        lamportMutex.onLockReleased(request);
        responseObserver.onNext(NodeProto.LockResponse.newBuilder().setGranted(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getStatus(NodeProto.StatusRequest request,
                          StreamObserver<NodeProto.StatusResponse> responseObserver) {
        // Return UNAVAILABLE when killed so the bridge correctly marks this node as DEAD
        if (isKilled.get()) {
            responseObserver.onError(Status.UNAVAILABLE.asException());
            return;
        }
        NodeProto.StatusResponse resp = NodeProto.StatusResponse.newBuilder()
                .setNodeId(state.getNodeId())
                .setRole(state.getRole().name())
                .setCurrentLeader(state.getCurrentLeader())
                .setCurrentTerm(state.getCurrentTerm())
                .setTasksExecuted(state.getTasksExecuted().get())
                .setIsAlive(true)
                .setLockedTask(state.getLockedTask())
                .setLamportClock(state.getLamportClock().get())
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }
}
