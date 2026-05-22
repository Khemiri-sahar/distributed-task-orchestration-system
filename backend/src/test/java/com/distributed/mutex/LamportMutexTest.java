package com.distributed.mutex;

import com.distributed.consensus.ConsensusManager;
import com.distributed.grpc.proto.NodeProto;
import com.distributed.state.NodeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class LamportMutexTest {

    private LamportMutex lamportMutex;
    private ConcurrentHashMap<String, Long> pendingRequests;

    @Mock
    private ConsensusManager consensusManager;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        NodeState state = new NodeState(3, Collections.emptyList());
        lamportMutex = new LamportMutex(state, consensusManager);

        // Use reflection to access pendingRequests
        Field f = LamportMutex.class.getDeclaredField("pendingRequests");
        f.setAccessible(true);
        pendingRequests = (ConcurrentHashMap<String, Long>) f.get(lamportMutex);
    }

    @Test
    public void onLockRequested_noPendingRequest_grantsImmediately() {
        NodeProto.LockRequest req = NodeProto.LockRequest.newBuilder()
                .setTaskId("task-1")
                .setRequesterId(1)
                .setLamportTimestamp(5)
                .build();

        NodeProto.LockResponse resp = lamportMutex.onLockRequested(req);

        assertTrue(resp.getGranted());
    }

    @Test
    public void onLockRequested_incomingTimestampLower_grants() {
        // Setup: node 3 has pending request with ts=5
        pendingRequests.put("task-mine", 5L);

        // Incoming ts=1, my ts=5 (they win because 1 < 5)
        NodeProto.LockRequest incomingReq = NodeProto.LockRequest.newBuilder()
                .setTaskId("task-incoming")
                .setRequesterId(1)
                .setLamportTimestamp(1)
                .build();

        NodeProto.LockResponse resp = lamportMutex.onLockRequested(incomingReq);

        assertTrue(resp.getGranted());
    }

    @Test
    public void onLockRequested_incomingTimestampHigher_defers() {
        // Setup: node 3 has pending request with ts=3
        pendingRequests.put("task-mine", 3L);

        // Incoming ts=9, my ts=3 (I win because 9 > 3)
        NodeProto.LockRequest incomingReq = NodeProto.LockRequest.newBuilder()
                .setTaskId("task-incoming")
                .setRequesterId(2)
                .setLamportTimestamp(9)
                .build();

        NodeProto.LockResponse resp = lamportMutex.onLockRequested(incomingReq);

        assertFalse(resp.getGranted());
    }

    @Test
    public void onLockRequested_tieBreakByNodeId_lowerIdWins() {
        // Setup: node 3 has pending request with ts=5
        pendingRequests.put("task-mine", 5L);

        // Same timestamps (5), requester id=1, me=3 (they win because 1 < 3)
        NodeProto.LockRequest incomingReq = NodeProto.LockRequest.newBuilder()
                .setTaskId("task-incoming")
                .setRequesterId(1)
                .setLamportTimestamp(5)
                .build();

        NodeProto.LockResponse resp = lamportMutex.onLockRequested(incomingReq);

        assertTrue(resp.getGranted());
    }
}
