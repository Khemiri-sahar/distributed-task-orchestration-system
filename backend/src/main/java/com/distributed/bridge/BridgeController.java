package com.distributed.bridge;

import com.distributed.grpc.proto.NodeProto;
import com.distributed.grpc.proto.NodeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BridgeController {

    private final Map<Integer, NodeServiceGrpc.NodeServiceBlockingStub> nodeStubs = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> taskLogs = new ConcurrentLinkedQueue<>();

    private static final Map<Integer, Integer> NODE_PORTS = Map.of(1, 50051, 2, 50052, 3, 50053);

    @PostConstruct
    public void initStubs() {
        NODE_PORTS.forEach((id, port) -> {
            ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext().build();
            nodeStubs.put(id, NodeServiceGrpc.newBlockingStub(ch));
        });
    }

    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getStatus() {
        List<Map<String, Object>> results = new ArrayList<>();
        nodeStubs.forEach((id, stub) -> {
            try {
                NodeProto.StatusResponse r = stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getStatus(NodeProto.StatusRequest.getDefaultInstance());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("nodeId", r.getNodeId());
                entry.put("role", r.getRole());
                entry.put("currentLeader", r.getCurrentLeader());
                entry.put("currentTerm", r.getCurrentTerm());
                entry.put("tasksExecuted", r.getTasksExecuted());
                entry.put("isAlive", true);
                entry.put("lockedTask", r.getLockedTask());
                entry.put("lamportClock", r.getLamportClock());
                results.add(entry);
            } catch (Exception e) {
                Map<String, Object> dead = new LinkedHashMap<>();
                dead.put("nodeId", id);
                dead.put("role", "DEAD");
                dead.put("isAlive", false);
                dead.put("currentLeader", -1);
                dead.put("currentTerm", 0);
                dead.put("tasksExecuted", 0);
                dead.put("lockedTask", "");
                dead.put("lamportClock", 0);
                results.add(dead);
            }
        });
        return ResponseEntity.ok(results);
    }

    @PostMapping("/task")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody Map<String, String> body) {
        String taskType = body.get("taskType");
        String payload = body.get("payload");
        String taskId = UUID.randomUUID().toString();

        List<Integer> nodeIds = new ArrayList<>(List.of(1, 2, 3));
        Collections.shuffle(nodeIds);

        NodeProto.TaskRequest req = NodeProto.TaskRequest.newBuilder()
                .setTaskId(taskId)
                .setTaskType(taskType)
                .setPayload(payload)
                .build();

        for (int nodeId : nodeIds) {
            try {
                NodeProto.TaskResponse resp = nodeStubs.get(nodeId)
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .submitTask(req);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("taskId", taskId);
                result.put("result", resp.getResult());
                result.put("executedBy", resp.getExecutedBy());
                result.put("redirected", resp.getRedirected());
                result.put("selectedNode", nodeId);
                result.put("success", resp.getSuccess());
                result.put("error", resp.getError());

                addToLogs(result);
                return ResponseEntity.ok(result);
            } catch (StatusRuntimeException e) {
                // Node unreachable — try next
            }
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "All nodes unreachable");
        return ResponseEntity.status(503).body(error);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<Map<String, Object>>> getLogs() {
        return ResponseEntity.ok(new ArrayList<>(taskLogs));
    }

    @PostMapping("/kill/{nodeId}")
    public ResponseEntity<Map<String, Object>> killNode(@PathVariable int nodeId) {
        try {
            RestTemplate rt = new RestTemplate();
            rt.postForEntity("http://localhost:800" + nodeId + "/internal/kill", null, String.class);
            return ResponseEntity.ok(Map.of("killed", nodeId));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", "Could not kill node"));
        }
    }

    @PostMapping("/revive/{nodeId}")
    public ResponseEntity<Map<String, Object>> reviveNode(@PathVariable int nodeId) {
        try {
            RestTemplate rt = new RestTemplate();
            rt.postForEntity("http://localhost:800" + nodeId + "/internal/revive", null, String.class);
            return ResponseEntity.ok(Map.of("revived", nodeId));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", "Could not revive node"));
        }
    }

    private void addToLogs(Map<String, Object> entry) {
        taskLogs.add(entry);
        while (taskLogs.size() > 50) {
            taskLogs.poll();
        }
    }
}
