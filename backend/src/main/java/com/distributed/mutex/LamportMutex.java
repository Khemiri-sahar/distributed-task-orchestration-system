package com.distributed.mutex;

import com.distributed.consensus.ConsensusManager;
import com.distributed.grpc.proto.NodeProto;
import com.distributed.grpc.proto.NodeServiceGrpc;
import com.distributed.state.NodeState;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class LamportMutex {

    private static final Logger log = LoggerFactory.getLogger(LamportMutex.class);

    private final NodeState state;
    private final ConsensusManager consensusManager;

    private final ReentrantLock mutexLock = new ReentrantLock();
    private final Condition allOksReceived = mutexLock.newCondition();
    private final ConcurrentHashMap<String, Long> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> deferredOks = new ConcurrentHashMap<>();
    private final Set<Integer> receivedOks = ConcurrentHashMap.newKeySet();

    private static final int LOCK_TIMEOUT_SECONDS = 5;

    public LamportMutex(NodeState state, ConsensusManager consensusManager) {
        this.state = state;
        this.consensusManager = consensusManager;
    }

    public void acquireLock(String taskId) {
        long ts = state.incrementLamportClock();
        pendingRequests.put(taskId, ts);
        receivedOks.clear();

        Map<Integer, NodeServiceGrpc.NodeServiceBlockingStub> peerStubs = consensusManager.getStubs();

        long aliveCount = peerStubs.keySet().stream()
                .filter(consensusManager::isPeerAlive)
                .count();

        for (Map.Entry<Integer, NodeServiceGrpc.NodeServiceBlockingStub> entry : peerStubs.entrySet()) {
            int peerId = entry.getKey();
            NodeServiceGrpc.NodeServiceBlockingStub stub = entry.getValue();

            if (!consensusManager.isPeerAlive(peerId)) continue;

            try {
                NodeProto.LockRequest req = NodeProto.LockRequest.newBuilder()
                        .setTaskId(taskId)
                        .setRequesterId(state.getNodeId())
                        .setLamportTimestamp(ts)
                        .build();
                NodeProto.LockResponse resp = stub.withDeadlineAfter(2, TimeUnit.SECONDS).acquireLock(req);
                if (resp.getGranted()) {
                    receivedOks.add(peerId);
                }
            } catch (StatusRuntimeException e) {
                log.warn("Peer {} unreachable during lock acquire — counting as OK", peerId);
                receivedOks.add(peerId);
            }
        }

        mutexLock.lock();
        try {
            long deadline = System.currentTimeMillis() + LOCK_TIMEOUT_SECONDS * 1000L;
            while (receivedOks.size() < aliveCount) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    log.warn("Lock acquire timed out after {}s — proceeding anyway", LOCK_TIMEOUT_SECONDS);
                    break;
                }
                allOksReceived.await(remaining, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutexLock.unlock();
        }

        state.setLockedTask(taskId);
        log.info("Node {} acquired lock for task {}", state.getNodeId(), taskId);
    }

    public void releaseLock(String taskId) {
        pendingRequests.remove(taskId);
        state.setLockedTask("");
        log.info("Node {} released lock for task {}", state.getNodeId(), taskId);

        // Send OK to any deferred peers
        Map<Integer, NodeServiceGrpc.NodeServiceBlockingStub> peerStubs = consensusManager.getStubs();
        deferredOks.forEach((peerId, deferredTaskId) -> {
            NodeServiceGrpc.NodeServiceBlockingStub stub = peerStubs.get(peerId);
            if (stub == null) return;
            try {
                NodeProto.LockRequest req = NodeProto.LockRequest.newBuilder()
                        .setTaskId(taskId)
                        .setRequesterId(state.getNodeId())
                        .setLamportTimestamp(state.getLamportClock().get())
                        .build();
                stub.withDeadlineAfter(2, TimeUnit.SECONDS).releaseLock(req);
            } catch (StatusRuntimeException e) {
                log.warn("Could not notify peer {} of lock release", peerId);
            }
        });
        deferredOks.clear();

        // Notify all alive peers of release
        peerStubs.forEach((peerId, stub) -> {
            if (!consensusManager.isPeerAlive(peerId)) return;
            try {
                NodeProto.LockRequest req = NodeProto.LockRequest.newBuilder()
                        .setTaskId(taskId)
                        .setRequesterId(state.getNodeId())
                        .setLamportTimestamp(state.getLamportClock().get())
                        .build();
                stub.withDeadlineAfter(2, TimeUnit.SECONDS).releaseLock(req);
            } catch (StatusRuntimeException e) {
                log.warn("Could not notify peer {} of lock release", peerId);
            }
        });
    }

    public NodeProto.LockResponse onLockRequested(NodeProto.LockRequest request) {
        state.updateLamportClock(request.getLamportTimestamp());

        if (pendingRequests.isEmpty()) {
            System.out.println("[LOCK] Node " + state.getNodeId() + " - NO pending request - GRANT to requester " + request.getRequesterId() + " (ts=" + request.getLamportTimestamp() + ")");
            return NodeProto.LockResponse.newBuilder().setGranted(true).build();
        }

        long myTs = pendingRequests.values().iterator().next();
        long theirTs = request.getLamportTimestamp();

        boolean theyWin = (theirTs < myTs) ||
                (theirTs == myTs && request.getRequesterId() < state.getNodeId());

        if (theyWin) {
            System.out.println("[LOCK] Node " + state.getNodeId() + " (ts=" + myTs + ") - GRANT to requester " + request.getRequesterId() + " (ts=" + theirTs + ") - they win");
            return NodeProto.LockResponse.newBuilder().setGranted(true).build();
        } else {
            deferredOks.put(request.getRequesterId(), request.getTaskId());
            System.out.println("[LOCK] Node " + state.getNodeId() + " (ts=" + myTs + ") - DEFER requester " + request.getRequesterId() + " (ts=" + theirTs + ") - I win");
            return NodeProto.LockResponse.newBuilder().setGranted(false).build();
        }
    }

    public void onLockReleased(NodeProto.LockRequest request) {
        state.updateLamportClock(request.getLamportTimestamp());
        int senderId = request.getRequesterId();
        receivedOks.add(senderId);
        mutexLock.lock();
        try {
            allOksReceived.signal();
        } finally {
            mutexLock.unlock();
        }
    }
}
