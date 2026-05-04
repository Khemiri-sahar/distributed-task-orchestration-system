package com.distributed.executor;

import com.distributed.grpc.proto.NodeProto;
import com.distributed.model.TaskLog;
import com.distributed.state.NodeState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
        if (payload.matches("^fibonacci:\\d+$")) {
            try {
                int n = Integer.parseInt(payload.split(":")[1]);
                long result = fibonacci(n);
                return TaskLog.of(taskId, taskType, payload, String.valueOf(result), nodeId, false, null);
            } catch (Exception e) {
                return TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                        "Fibonacci error: " + e.getMessage());
            }
        }

        if (!payload.matches("^[\\d\\s\\+\\-\\*\\/\\(\\)\\.]+$")) {
            return TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                    "Invalid expression. Use arithmetic (e.g. 2+3*4) or fibonacci:N");
        }

        Matcher m = Pattern.compile("^([\\d.]+)\\s*([+\\-*/])\\s*([\\d.]+)$")
                .matcher(payload.trim());
        if (!m.matches()) {
            return TaskLog.of(taskId, taskType, payload, null, nodeId, false,
                    "Use format: a+b, a-b, a*b, a/b, or fibonacci:N");
        }

        double a = Double.parseDouble(m.group(1));
        String op = m.group(2);
        double b = Double.parseDouble(m.group(3));

        double result;
        switch (op) {
            case "+" -> result = a + b;
            case "-" -> result = a - b;
            case "*" -> result = a * b;
            case "/" -> {
                if (b == 0) return TaskLog.of(taskId, taskType, payload, null, nodeId, false, "Division by zero");
                result = a / b;
            }
            default -> { return TaskLog.of(taskId, taskType, payload, null, nodeId, false, "Unknown operator"); }
        }

        String formatted = result == Math.floor(result) && !Double.isInfinite(result)
                ? String.valueOf((long) result)
                : String.valueOf(result);
        return TaskLog.of(taskId, taskType, payload, formatted, nodeId, false, null);
    }

    private long fibonacci(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;
        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            long c = a + b;
            a = b;
            b = c;
        }
        return b;
    }

    private TaskLog handleMessage(String taskId, String taskType, String payload, int nodeId) {
        String reversed = new StringBuilder(payload).reverse().toString();
        return TaskLog.of(taskId, taskType, payload, reversed, nodeId, false, null);
    }
}
