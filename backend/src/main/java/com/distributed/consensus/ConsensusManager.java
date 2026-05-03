package com.distributed.consensus;

import com.distributed.grpc.NodeGrpcServer;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ConsensusManager {

    private final NodeState state;

    private final Map<Integer, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<Integer, NodeServiceGrpc.NodeServiceBlockingStub> stubs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<Integer, Long> lastHeartbeatFromPeer = new ConcurrentHashMap<>();

    // Guards so broadcaster and watchdog are each scheduled exactly once
    private final AtomicBoolean broadcasterStarted = new AtomicBoolean(false);
    private final AtomicBoolean watchdogStarted = new AtomicBoolean(false);

    private static final int HEARTBEAT_INTERVAL_MS = 1000;
    private static final int HEARTBEAT_TIMEOUT_MS  = 3000;

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
                NodeServiceGrpc.NodeServiceBlockingStub stub = NodeServiceGrpc.newBlockingStub(channel);

                channels.put(peerId, channel);
                stubs.put(peerId, stub);
            }

            probeAllPeers();

            boolean hasLowerAlive = stubs.keySet().stream()
                    .anyMatch(peerId -> peerId < state.getNodeId() && isPeerAlive(peerId));

            if (!hasLowerAlive) {
                state.setRole(NodeRole.LEADER);
                state.setCurrentLeader(state.getNodeId());
            } else {
                state.setRole(NodeRole.FOLLOWER);
            }

            // Start both loops once — they are gated internally by role/killed checks
            startHeartbeatBroadcaster();
            startHeartbeatWatchdog();

            System.out.println("Node " + state.getNodeId() + " started as " + state.getRole()
                    + ". Leader: " + state.getCurrentLeader());

        }, 2, TimeUnit.SECONDS);
    }

    private void probeAllPeers() {
        stubs.forEach((peerId, stub) -> {
            try {
                // Use getStatus (read-only) so a candidate checking liveness never
                // triggers onHeartbeatReceived on the current leader and demotes it.
                stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getStatus(NodeProto.StatusRequest.getDefaultInstance());
                lastHeartbeatFromPeer.put(peerId, System.currentTimeMillis());
            } catch (StatusRuntimeException e) {
                System.out.println("Peer " + peerId + " unreachable during probe");
                lastHeartbeatFromPeer.remove(peerId);
            }
        });
    }

    private void startHeartbeatBroadcaster() {
        if (!broadcasterStarted.compareAndSet(false, true)) return;
        scheduler.scheduleAtFixedRate(() -> {
            if (NodeGrpcServer.isKilled()) return;
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
        if (!watchdogStarted.compareAndSet(false, true)) return;
        scheduler.scheduleAtFixedRate(() -> {
            if (NodeGrpcServer.isKilled()) return;
            if (state.getRole() == NodeRole.LEADER) return;

            long timeSinceLastBeat = System.currentTimeMillis() - state.getLastHeartbeatMs();
            if (timeSinceLastBeat > HEARTBEAT_TIMEOUT_MS) {
                System.out.println("Node " + state.getNodeId() + " — heartbeat timeout, triggering re-election");
                triggerReElection();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void triggerReElection() {
        // Probe first (read-only getStatus — no side effects on peers)
        probeAllPeers();

        boolean hasLowerAlive = stubs.keySet().stream()
                .anyMatch(peerId -> peerId < state.getNodeId() && isPeerAlive(peerId));

        if (!hasLowerAlive) {
            // No alive node with lower ID — compete for leadership
            state.setRole(NodeRole.CANDIDATE);
            state.setCurrentTerm(state.getCurrentTerm() + 1);
            state.setRole(NodeRole.LEADER);
            state.setCurrentLeader(state.getNodeId());
            System.out.println("Node " + state.getNodeId() + " elected as new LEADER for term "
                    + state.getCurrentTerm());
        } else {
            // A lower-ID node is alive — stand down without bumping the term.
            // The existing leader's heartbeats will still satisfy shouldFollow.
            state.setRole(NodeRole.FOLLOWER);
            state.setLastHeartbeatMs(System.currentTimeMillis());
        }
    }

    public void onHeartbeatReceived(int leaderId, int term) {
        state.setLastHeartbeatMs(System.currentTimeMillis());
        lastHeartbeatFromPeer.put(leaderId, System.currentTimeMillis());

        boolean shouldFollow = term > state.getCurrentTerm() ||
                (term == state.getCurrentTerm() && leaderId < state.getNodeId());

        if (shouldFollow) {
            state.setCurrentLeader(leaderId);
            state.setCurrentTerm(term);
            if (state.getRole() != NodeRole.FOLLOWER) {
                System.out.println("Node " + state.getNodeId()
                        + " demoted to FOLLOWER — node " + leaderId + " is leader for term " + term);
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
