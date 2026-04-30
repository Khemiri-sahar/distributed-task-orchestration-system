package com.distributed.state;

import com.distributed.model.NodeRole;
import com.distributed.model.TaskLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NodeState {

    private final int nodeId;
    private volatile NodeRole role = NodeRole.FOLLOWER;
    private volatile int currentLeader = -1;
    private volatile int currentTerm = 0;
    private final AtomicInteger tasksExecuted = new AtomicInteger(0);
    private final AtomicLong lamportClock = new AtomicLong(0);
    private volatile String lockedTask = "";
    private final List<String> peers;
    private volatile long lastHeartbeatMs = System.currentTimeMillis();
    private final ConcurrentLinkedQueue<TaskLog> taskLogs = new ConcurrentLinkedQueue<>();
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();

    public NodeState(int nodeId, List<String> peers) {
        this.nodeId = nodeId;
        this.peers = new ArrayList<>(peers);
    }

    public long incrementLamportClock() {
        return lamportClock.incrementAndGet();
    }

    public void updateLamportClock(long received) {
        lamportClock.updateAndGet(local -> Math.max(local, received) + 1);
    }

    public void addTaskLog(TaskLog log) {
        taskLogs.add(log);
        while (taskLogs.size() > 50) {
            taskLogs.poll();
        }
    }

    public List<TaskLog> getTaskLogs() {
        return new ArrayList<>(taskLogs);
    }

    public int getNodeId() {
        return nodeId;
    }

    public NodeRole getRole() {
        stateLock.readLock().lock();
        try {
            return role;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void setRole(NodeRole role) {
        stateLock.writeLock().lock();
        try {
            this.role = role;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public int getCurrentLeader() {
        stateLock.readLock().lock();
        try {
            return currentLeader;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void setCurrentLeader(int currentLeader) {
        stateLock.writeLock().lock();
        try {
            this.currentLeader = currentLeader;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public int getCurrentTerm() {
        stateLock.readLock().lock();
        try {
            return currentTerm;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void setCurrentTerm(int currentTerm) {
        stateLock.writeLock().lock();
        try {
            this.currentTerm = currentTerm;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public AtomicInteger getTasksExecuted() {
        return tasksExecuted;
    }

    public AtomicLong getLamportClock() {
        return lamportClock;
    }

    public String getLockedTask() {
        stateLock.readLock().lock();
        try {
            return lockedTask;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void setLockedTask(String lockedTask) {
        stateLock.writeLock().lock();
        try {
            this.lockedTask = lockedTask;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public List<String> getPeers() {
        return peers;
    }

    public long getLastHeartbeatMs() {
        stateLock.readLock().lock();
        try {
            return lastHeartbeatMs;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public void setLastHeartbeatMs(long lastHeartbeatMs) {
        stateLock.writeLock().lock();
        try {
            this.lastHeartbeatMs = lastHeartbeatMs;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public ReentrantReadWriteLock getStateLock() {
        return stateLock;
    }
}
