#!/bin/bash
pkill -f "distributed-task-orchestration" || true
pkill -f "BridgeApp" || true
pkill -f "vite" || true
echo "All processes stopped."
