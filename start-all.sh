#!/bin/bash
set -e

echo "Building backend..."
cd backend
./gradlew bootJar -x test
JAR=$(ls build/libs/*.jar | grep -v plain | head -1)
cd ..

echo "Starting Node 1..."
NODE_ID=1 GRPC_PORT=50051 REST_PORT=8001 java -jar backend/$JAR > /tmp/node1.log 2>&1 &
NODE1_PID=$!

echo "Starting Node 2..."
NODE_ID=2 GRPC_PORT=50052 REST_PORT=8002 java -jar backend/$JAR > /tmp/node2.log 2>&1 &
NODE2_PID=$!

echo "Starting Node 3..."
NODE_ID=3 GRPC_PORT=50053 REST_PORT=8003 java -jar backend/$JAR > /tmp/node3.log 2>&1 &
NODE3_PID=$!

echo "Waiting 5s for nodes to boot..."
sleep 5

echo "Starting Bridge..."
REST_PORT=8000 java -Dloader.main=com.distributed.bridge.BridgeApp \
  -jar backend/$JAR > /tmp/bridge.log 2>&1 &
BRIDGE_PID=$!

echo "Starting Frontend..."
cd frontend && npm install --silent && npm run dev &
FRONTEND_PID=$!
cd ..

echo ""
echo "======================================"
echo "System running:"
echo "  Node 1:   localhost:50051 (gRPC) | localhost:8001 (REST)"
echo "  Node 2:   localhost:50052 (gRPC) | localhost:8002 (REST)"
echo "  Node 3:   localhost:50053 (gRPC) | localhost:8003 (REST)"
echo "  Bridge:   localhost:8000"
echo "  Frontend: localhost:5173"
echo "======================================"
echo ""
echo "Logs: /tmp/node1.log  /tmp/node2.log  /tmp/node3.log  /tmp/bridge.log"
echo "Stop: ./stop-all.sh"
echo ""

wait
