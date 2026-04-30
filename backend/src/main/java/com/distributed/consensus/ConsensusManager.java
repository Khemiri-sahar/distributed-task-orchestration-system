package com.distributed.consensus;

import com.distributed.grpc.proto.NodeProto;
import com.distributed.grpc.proto.NodeServiceGrpc;
import com.distributed.model.NodeRole;
import com.distributed.state.NodeState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class ConsensusManager {

    private final NodeState state;

    private final Map<Integer, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<Integer, NodeServiceGrpc.NodeServiceBlockingStub> stubs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<Integer, Long> lastHeartbeatFromPeer = new ConcurrentHashMap<>();

    private static final int HEARTBEAT_INTERVAL_MS = 1000;
    private static final int HEARTBEAT_TIMEOUT_MS = 3000;

    // Map well-known ports to node IDs
    private static final Map<Integer, Integer> PORT_TO_NODE_ID = Map.of(
            50051, 1,
            50052, 2,
            50053, 3
    );

    public ConsensusManager(NodeState state) {
        this.state = state;
    }

    public void start() {
        scheduler.schedule(() -> {
            for (String peer : state.getPeers()) {
                String[] parts = peer.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                int peerId = PORT_TO_NODE_ID.getOrDefault(port, port);

                if (peerId == state.getNodeId()) continue;

                ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build();
                NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(2, TimeUnit.SECONDS);

                channels.put(peerId, channel);
                stubs.put(peerId, stub);
            }

            probeAllPeers();

            boolean hasLowerAlive = stubs.keySet().stream()
                    .anyMatch(peerId -> peerId < state.getNodeId() && isPeerAlive(peerId));

            if (!hasLowerAlive) {
                state.setRole(NodeRole.LEADER);
                state.setCurrentLeader(state.getNodeId());
                startHeartbeatBroadcaster();
            } else {
                state.setRole(NodeRole.FOLLOWER);
                startHeartbeatWatchdog();
            }

            System.out.println("Node " + state.getNodeId() + " started as " + state.getRole()
                    + ". Leader: " + state.getCurrentLeader());

        }, 2, TimeUnit.SECONDS);
    }

    private void probeAllPeers() {
        stubs.forEach((peerId, stub) -> {
            try {
                NodeProto.HeartbeatRequest req = NodeProto.HeartbeatRequest.newBuilder()
                        .setLeaderId(state.getNodeId())
                        .setTerm(state.getCurrentTerm())
                        .build();
                stub.withDeadlineAfter(2, TimeUnit.SECONDS).sendHeartbeat(req);
                lastHeartbeatFromPeer.put(peerId, System.currentTimeMillis());
            } catch (StatusRuntimeException e) {
                System.out.println("Peer " + peerId + " unreachable during probe");
            }
        });
    }

    private void startHeartbeatBroadcaster() {
        scheduler.scheduleAtFixedRate(() -> {
            if (state.getRole() != NodeRole.LEADER) return;

            stubs.forEach((peerId, stub) -> {
                try {
                    NodeProto.HeartbeatRequest req = NodeProto.HeartbeatRequest.newBuilder()
                            .setLeaderId(state.getNodeId())
                            .setTerm(state.getCurrentTerm())
                            .build();
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS).sendHeartbeat(req);
                } catch (StatusRuntimeException e) {
                    System.out.println("Peer " + peerId + " did not respond to heartbeat");
                }
            });
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startHeartbeatWatchdog() {
        scheduler.scheduleAtFixedRate(() -> {
            if (state.getRole() == NodeRole.LEADER) return;

            long timeSinceLastBeat = System.currentTimeMillis() - state.getLastHeartbeatMs();
            if (timeSinceLastBeat > HEARTBEAT_TIMEOUT_MS) {
                System.out.println("Heartbeat timeout — triggering re-election");
                triggerReElection();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void triggerReElection() {
        state.setRole(NodeRole.CANDIDATE);
        state.setCurrentTerm(state.getCurrentTerm() + 1);

        probeAllPeers();

        boolean hasLowerAlive = stubs.keySet().stream()
                .anyMatch(peerId -> peerId < state.getNodeId() && isPeerAlive(peerId));

        if (!hasLowerAlive) {
            state.setRole(NodeRole.LEADER);
            state.setCurrentLeader(state.getNodeId());
            startHeartbeatBroadcaster();
            System.out.println("Node " + state.getNodeId() + " elected as new LEADER for term "
                    + state.getCurrentTerm());
        } else {
            state.setRole(NodeRole.FOLLOWER);
            state.setLastHeartbeatMs(System.currentTimeMillis());
        }
    }

    public void onHeartbeatReceived(int leaderId, int term) {
        state.setLastHeartbeatMs(System.currentTimeMillis());
        lastHeartbeatFromPeer.put(leaderId, System.currentTimeMillis());

        if (term >= state.getCurrentTerm() && leaderId < state.getNodeId()) {
            state.setCurrentLeader(leaderId);
            state.setCurrentTerm(term);
            if (state.getRole() == NodeRole.LEADER) {
                System.out.println("Demoting self — lower node " + leaderId + " is leader");
                state.setRole(NodeRole.FOLLOWER);
            }
        }
    }

    public Map<Integer, NodeServiceGrpc.NodeServiceBlockingStub> getStubs() {
        return stubs;
    }

    public NodeServiceGrpc.NodeServiceBlockingStub getLeaderStub() {
        return stubs.get(state.getCurrentLeader());
    }

    public boolean isPeerAlive(int peerId) {
        return lastHeartbeatFromPeer.getOrDefault(peerId, 0L) > System.currentTimeMillis() - 5000;
    }
}
