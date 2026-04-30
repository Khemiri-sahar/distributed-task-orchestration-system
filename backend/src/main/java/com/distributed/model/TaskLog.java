package com.distributed.model;

import java.time.LocalDateTime;

public class TaskLog {

    private String taskId;
    private String taskType;
    private String payload;
    private String result;
    private int executedBy;
    private boolean redirected;
    private LocalDateTime timestamp;
    private String error;

    public static TaskLog of(String taskId, String taskType, String payload,
                             String result, int executedBy, boolean redirected, String error) {
        TaskLog log = new TaskLog();
        log.taskId = taskId;
        log.taskType = taskType;
        log.payload = payload;
        log.result = result;
        log.executedBy = executedBy;
        log.redirected = redirected;
        log.error = error;
        log.timestamp = LocalDateTime.now();
        return log;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public int getExecutedBy() { return executedBy; }
    public void setExecutedBy(int executedBy) { this.executedBy = executedBy; }

    public boolean isRedirected() { return redirected; }
    public void setRedirected(boolean redirected) { this.redirected = redirected; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
