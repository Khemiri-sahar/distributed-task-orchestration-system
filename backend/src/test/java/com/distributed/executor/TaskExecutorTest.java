package com.distributed.executor;

import com.distributed.grpc.proto.NodeProto;
import com.distributed.model.TaskLog;
import com.distributed.state.NodeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class TaskExecutorTest {

    private TaskExecutor executor;

    @BeforeEach
    public void setup() {
        executor = new TaskExecutor(new TaskLogger());
    }

    @Test
    public void compute_basicAddition_returnsCorrectResult() {
        NodeProto.TaskRequest req = NodeProto.TaskRequest.newBuilder()
                .setTaskId("t-add")
                .setTaskType("compute")
                .setPayload("10+5")
                .build();

        NodeState state = new NodeState(1, Collections.emptyList());
        TaskLog log = executor.execute(req, 1, state);

        assertNull(log.getError());
        assertEquals("15", log.getResult());
    }

    @Test
    public void compute_divisionByZero_returnsError() {
        NodeProto.TaskRequest req = NodeProto.TaskRequest.newBuilder()
                .setTaskId("t-div0")
                .setTaskType("compute")
                .setPayload("10/0")
                .build();

        NodeState state = new NodeState(1, Collections.emptyList());
        TaskLog log = executor.execute(req, 1, state);

        assertNull(log.getResult());
        assertNotNull(log.getError());
        assertEquals("Division by zero", log.getError());
    }

    @Test
    public void compute_fibonacci_returnsCorrectValue() {
        NodeProto.TaskRequest req = NodeProto.TaskRequest.newBuilder()
                .setTaskId("t-fib")
                .setTaskType("compute")
                .setPayload("fibonacci:10")
                .build();

        NodeState state = new NodeState(1, Collections.emptyList());
        TaskLog log = executor.execute(req, 1, state);

        assertNull(log.getError());
        assertEquals("55", log.getResult());
    }

    @Test
    public void message_reversePayload_returnsReversed() {
        NodeProto.TaskRequest req = NodeProto.TaskRequest.newBuilder()
                .setTaskId("t-msg")
                .setTaskType("message")
                .setPayload("hello")
                .build();

        NodeState state = new NodeState(1, Collections.emptyList());
        TaskLog log = executor.execute(req, 1, state);

        assertNull(log.getError());
        assertEquals("olleh", log.getResult());
    }

    @Test
    public void unknownTaskType_returnsErrorLog() {
        NodeProto.TaskRequest req = NodeProto.TaskRequest.newBuilder()
                .setTaskId("t-unknown")
                .setTaskType("sort")
                .setPayload("data")
                .build();

        NodeState state = new NodeState(1, Collections.emptyList());
        TaskLog log = executor.execute(req, 1, state);

        assertNotNull(log.getError());
        assertTrue(log.getError().contains("Unknown task type"));
    }
}
