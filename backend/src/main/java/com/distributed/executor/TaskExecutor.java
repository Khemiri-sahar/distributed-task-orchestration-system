package com.distributed.executor;

import com.distributed.grpc.proto.NodeProto;
import com.distributed.model.TaskLog;
import com.distributed.state.NodeState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

@Service("nodeTaskExecutor")
public class TaskExecutor {

    @Autowired
    private TaskLogger taskLogger;

    public TaskLog execute(NodeProto.TaskRequest request, int nodeId, NodeState state) {
        String taskId = request.getTaskId();
        String taskType = request.getTaskType();
        String payload = request.getPayload();

        TaskLog log;

        switch (taskType) {
            case "compute" -> log = handleCompute(taskId, taskType, payload, nodeId);
            case "message" -> log = handleMessage(taskId, taskType, payload, nodeId);
            default -> log = TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                    "Unknown task type: " + taskType);
        }

        state.getTasksExecuted().incrementAndGet();
        state.addTaskLog(log);
        taskLogger.writeLog(log, nodeId);

        return log;
    }

    private TaskLog handleCompute(String taskId, String taskType, String payload, int nodeId) {
        if (!payload.matches("^[\\d\\s\\+\\-\\*\\/\\(\\)\\.]+$")) {
            return TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                    "Invalid expression characters");
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
        if (engine == null) {
            engine = new ScriptEngineManager().getEngineByName("javascript");
        }
        if (engine == null) {
            return TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                    "No script engine available");
        }

        try {
            Object result = engine.eval(payload);
            return TaskLog.of(taskId, taskType, payload, String.valueOf(result), nodeId, false, null);
        } catch (ScriptException e) {
            return TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                    "Evaluation failed: " + e.getMessage());
        }
    }

    private TaskLog handleMessage(String taskId, String taskType, String payload, int nodeId) {
        String reversed = new StringBuilder(payload).reverse().toString();
        return TaskLog.of(taskId, taskType, payload, reversed, nodeId, false, null);
    }
}
