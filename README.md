# Distributed Task Orchestration System

A three-node distributed system with leader election, Lamport mutual exclusion, and a React dashboard.

---

![Three-node dashboard](images/nodes_main_screen.png)

## Architecture

The backend consists of three independent Java nodes that communicate exclusively over **gRPC**. Each node runs a gRPC server and exposes five RPCs: `submitTask`, `sendHeartbeat`, `getStatus`, `acquireLock`, and `releaseLock`. Leader election follows a simplified Raft-inspired protocol: on startup each node probes its peers using `getStatus`; the node with the lowest ID among reachable nodes claims the LEADER role and begins broadcasting heartbeats every second. Followers run a watchdog that triggers re-election after a 3-second heartbeat timeout. When a node re-joins after a crash, it probes peers before ever incrementing its term — if a lower-ID node is already alive, it stands down without disrupting the existing leader.

Task execution uses **Lamport logical clocks** for mutual exclusion. Before executing a task the leader acquires a distributed lock by broadcasting `acquireLock` RPCs to all peers; peers grant or defer based on Lamport timestamp ordering, preventing concurrent task execution across nodes. The **Bridge** is a lightweight Spring Boot application that sits between the React frontend and the gRPC cluster: it exposes a REST/JSON API on port 8000, translates HTTP requests into gRPC calls, fans out status polls to all three nodes, and handles leader-not-found by trying nodes in random order until one accepts the task. The React frontend polls `/api/status` every 2 seconds and visualises the live consensus state with animated SVG topology, per-node cards, and a task dispatch form.


**Topology Diagram**

```mermaid
flowchart LR
	F[React Frontend]
	B[Bridge (REST)]
	subgraph Cluster[Cluster (gRPC)]
		direction TB
		N1[Node 1\ngRPC:50051\nREST:8001]
		N2[Node 2\ngRPC:50052\nREST:8002]
		N3[Node 3\ngRPC:50053\nREST:8003]
	end

	F -->|HTTP /api| B
	B -->|gRPC (submit / status)| N1
	B -->|gRPC (submit / status)| N2
	B -->|gRPC (submit / status)| N3

	N1 <-->|gRPC peer| N2
	N2 <-->|gRPC peer| N3
	N1 <-->|gRPC peer| N3
```

**Bridge API**

| Endpoint | Method | Description |
|---|---:|---|
| `/api/status` | GET | Aggregated cluster status (polled by frontend) |
| `/api/task` | POST | Submit a task JSON: `{ "type": "compute|message", "payload": "..." }` |
| `/internal/kill` | POST | Simulate node crash (body: `{ "nodeId": <n> }`) |
| `/internal/revive` | POST | Revive a previously killed node (body: `{ "nodeId": <n> }`) |

**Design Decisions (ADR)**

- Leader election: deterministic lowest-node-ID wins for demo reproducibility; mutual exclusion uses Lamport logical clocks to order lock requests and ensure fairness.


## Prerequisites
# Distributed Task Orchestration System

A compact demo platform that runs a three-node distributed cluster with leader election, Lamport mutual exclusion for task execution, and a React dashboard for monitoring and interaction.

This repository contains:

- A Java Spring Boot backend that can run as multiple independent nodes (gRPC + REST bridge).
- A lightweight Bridge (Spring Boot) that exposes a REST API to the frontend and forwards requests to the gRPC nodes.
- A React frontend (Vite) that visualises cluster state and lets you submit tasks.

This README describes how to build, run, and test the system locally, and how to run it using Docker Compose.

---

## Highlights

- Leader election with a lowest-ID priority rule for deterministic demos.
- Lamport-clock-based mutual exclusion (`LamportMutex`) for distributed task execution.
- Task types: `compute` (arithmetic expressions, Fibonacci) and `message` (string reverse).
- REST Bridge that exposes `/api/status`, `/api/task`, `/api/logs`, and node control endpoints (`/internal/kill`, `/internal/revive`).

---

## Prerequisites

- Java 17+
- Docker & Docker Compose (if using the provided compose setup)
- Node.js (only required for local frontend development)

---

## Quickstart (Docker Compose)

The repository includes a `docker-compose.yml` to run three nodes, the bridge, and the frontend.

To build and start the stack:

```bash
docker compose up --build
```

Services exposed locally:

- Frontend: http://localhost:5173
- Bridge REST API: http://localhost:8000/api
- Node REST endpoints: http://localhost:8001, :8002, :8003 (internal control)
- gRPC ports: 50051, 50052, 50053 (for inter-node and bridge → node traffic)

Stop the stack:

```bash
docker compose down
```

---

## Local Development (manual)

Build backend jar:

```bash
cd backend
./gradlew bootJar -x test

# Run nodes (three separate shells)
NODE_ID=1 GRPC_PORT=50051 REST_PORT=8001 java -jar build/libs/*.jar
NODE_ID=2 GRPC_PORT=50052 REST_PORT=8002 java -jar build/libs/*.jar
NODE_ID=3 GRPC_PORT=50053 REST_PORT=8003 java -jar build/libs/*.jar

# Run the Bridge (in a separate shell)
REST_PORT=8000 java -Dloader.main=com.distributed.bridge.BridgeApp -jar build/libs/*.jar

# Frontend (development)
cd frontend
npm install
npm run dev
```

---

## Tests

Run backend unit tests:

```bash
cd backend
./gradlew test
```

Known test targets in this repository include unit tests for `TaskExecutor` and `LamportMutex`.

---

## Troubleshooting

- If a node does not start, check the logs printed in the container (or `/tmp/*.log` when using the provided shell scripts).
- The bridge uses configured host/port lists to connect to nodes; when running under Docker Compose services are reachable by their service names.

---


