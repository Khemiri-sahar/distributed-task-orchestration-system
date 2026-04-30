package com.distributed.executor;

import com.distributed.model.TaskLog;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class TaskLogger {

    public void writeLog(TaskLog log, int nodeId) {
        try {
            Files.createDirectories(Paths.get("logs"));
            String path = "logs/node_" + nodeId + ".log";
            String line = String.format("[%s] [%s] [%s] PAYLOAD=\"%s\" RESULT=\"%s\" ERROR=\"%s\"%n",
                    log.getTimestamp(), log.getTaskId(), log.getTaskType(),
                    log.getPayload(), log.getResult(), log.getError());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path, true))) {
                writer.write(line);
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to write task log: " + e.getMessage());
        }
    }
}
