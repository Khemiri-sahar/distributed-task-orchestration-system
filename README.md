# Distributed Task Orchestration System

A three-node distributed system with leader election, Lamport mutual exclusion, and a React dashboard.

---

## Architecture

The backend consists of three independent Java nodes that communicate exclusively over **gRPC**. Each node runs a gRPC server and exposes five RPCs: `submitTask`, `sendHeartbeat`, `getStatus`, `acquireLock`, and `releaseLock`. Leader election follows a simplified Raft-inspired protocol: on startup each node probes its peers using `getStatus`; the node with the lowest ID among reachable nodes claims the LEADER role and begins broadcasting heartbeats every second. Followers run a watchdog that triggers re-election after a 3-second heartbeat timeout. When a node re-joins after a crash, it probes peers before ever incrementing its term — if a lower-ID node is already alive, it stands down without disrupting the existing leader.

Task execution uses **Lamport logical clocks** for mutual exclusion. Before executing a task the leader acquires a distributed lock by broadcasting `acquireLock` RPCs to all peers; peers grant or defer based on Lamport timestamp ordering, preventing concurrent task execution across nodes. The **Bridge** is a lightweight Spring Boot application that sits between the React frontend and the gRPC cluster: it exposes a REST/JSON API on port 8000, translates HTTP requests into gRPC calls, fans out status polls to all three nodes, and handles leader-not-found by trying nodes in random order until one accepts the task. The React frontend polls `/api/status` every 2 seconds and visualises the live consensus state with animated SVG topology, per-node cards, and a task dispatch form.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Node.js | 18+ |
| Gradle | 8+ (or use the included `./gradlew` wrapper) |

---

## Setup & Run

```bash
# 1. Clone the repository
git clone <repo-url>
cd distributed-task-orchestration-system

# 2. Make scripts executable
chmod +x start-all.sh stop-all.sh

# 3. Start everything (builds backend, starts 3 nodes + bridge + frontend)
./start-all.sh
```

Open **http://localhost:5173** in your browser.

To stop all processes:

```bash
./stop-all.sh
```

---

## Manual Startup (alternative)

Open five terminals from the project root:

```bash
# Terminal 1 — build
cd backend && ./gradlew bootJar -x test

# Terminal 2 — Node 1 (leader on first boot)
NODE_ID=1 GRPC_PORT=50051 REST_PORT=8001 \
  java -jar backend/build/libs/distributed-task-orchestration-system-*.jar

# Terminal 3 — Node 2
NODE_ID=2 GRPC_PORT=50052 REST_PORT=8002 \
  java -jar backend/build/libs/distributed-task-orchestration-system-*.jar

# Terminal 4 — Node 3
NODE_ID=3 GRPC_PORT=50053 REST_PORT=8003 \
  java -jar backend/build/libs/distributed-task-orchestration-system-*.jar

# Terminal 5 — Bridge (wait ~5s for nodes to be ready)
REST_PORT=8000 java -Dloader.main=com.distributed.bridge.BridgeApp \
  -jar backend/build/libs/distributed-task-orchestration-system-*.jar

# Terminal 6 — Frontend
cd frontend && npm install && npm run dev
```

---

## Testing the System

### 1. Submit a compute task

- Task type: `compute`
- Payload: `100 + 50 * 2`
- Expected result: `200.0`

The expression is evaluated by the leader and the result appears in the task log.

### 2. Submit a message task

- Task type: `message`
- Payload: `Hello Distributed`
- Expected result: `detubirtsiD olleH`

The message is reversed character-by-character by the executing node.

### 3. Test fault tolerance

1. In the dashboard, click **⚡ KILL** on Node 1 (the current leader).
2. Watch the ConsensusViz — after ~4 seconds the CANDIDATE animation appears on Node 2 or 3, then one becomes LEADER.
3. Submit any task — it should execute on the new leader (`executedBy` in the result).

### 4. Test task redirection

The bridge picks a node at random. When it picks a follower:

1. The follower forwards the task to the leader via gRPC.
2. The response comes back with `"redirected": true`.
3. The `↪` column in the task log will be marked.

### 5. Revive Node 1

1. Click **↺ REVIVE** on Node 1.
2. Node 1 probes its peers, finds the current leader alive, and rejoins as **FOLLOWER**.
3. The existing leader keeps its role — no unnecessary re-election.

---

## Project Structure

```
.
├── start-all.sh               # One-command startup
├── stop-all.sh                # Kill all processes
├── backend/
│   ├── proto/                 # Protobuf definitions (NodeService)
│   └── src/main/java/com/distributed/
│       ├── DistributedApp.java          # Node entry point
│       ├── bridge/
│       │   ├── BridgeApp.java           # Bridge entry point
│       │   ├── BridgeController.java    # REST → gRPC translation
│       │   └── InternalController.java  # /internal/kill & /revive
│       ├── consensus/
│       │   └── ConsensusManager.java    # Heartbeat, election, watchdog
│       ├── executor/
│       │   └── TaskExecutor.java        # compute / message handlers
│       ├── grpc/
│       │   └── NodeGrpcServer.java      # gRPC service implementation
│       ├── model/
│       │   ├── NodeRole.java            # LEADER / FOLLOWER / CANDIDATE
│       │   └── TaskLog.java
│       ├── mutex/
│       │   └── LamportMutex.java        # Distributed lock
│       └── state/
│           └── NodeState.java           # Per-node mutable state
└── frontend/
    └── src/
        ├── api/bridge.js                # Axios wrappers for Bridge REST API
        ├── App.jsx                      # Root layout + 2s polling loop
        └── components/
            ├── ConsensusViz.jsx         # Animated SVG cluster topology
            ├── NodeCard.jsx             # Per-node status card
            ├── TaskSubmitter.jsx        # Task dispatch form
            ├── TaskLog.jsx              # Scrollable result table
            └── ErrorBanner.jsx         # Bridge-offline warning
```

---

## Known Limitations

- **Leader election** uses a lowest-ID priority rule rather than full Raft vote counting. This means the node with the smallest ID always wins when it is alive — suitable for demos, not for production skew scenarios.
- **Lock timeout** is 5 seconds, hardcoded in `LamportMutex.java`. Under high contention with slow nodes this can cause tasks to fail.
- **Bridge logs** are in-memory only and reset on each Bridge restart.
