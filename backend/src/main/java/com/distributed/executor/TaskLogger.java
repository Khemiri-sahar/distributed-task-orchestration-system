package com.distributed.executor;

import com.distributed.model.TaskLog;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskLogger {

    public void writeLog(TaskLog log, int nodeId) {
        try {
            Files.createDirectories(Paths.get("logs"));
            String path = "logs/node_" + nodeId + ".log";
            String equation = null;
            if ("compute".equals(log.getTaskType())) {
                String payload = log.getPayload() == null ? "" : log.getPayload().trim();
                if (payload.startsWith("fibonacci:")) {
                    try {
                        String n = payload.split(":")[1];
                        equation = "fibonacci(" + n + ") = " + (log.getResult() == null ? "null" : log.getResult());
                    } catch (Exception ignored) {
                        equation = payload + " = " + (log.getResult() == null ? "null" : log.getResult());
                    }
                } else {
                    String compact = payload.replaceAll("\\s+", "");
                    Matcher m = Pattern.compile("([\\d.]+)([+\\-*/])([\\d.]+)").matcher(compact);
                    if (m.matches()) {
                        String a = m.group(1);
                        String op = m.group(2);
                        String b = m.group(3);
                        equation = a + " " + op + " " + b + " = " + (log.getResult() == null ? "null" : log.getResult());
                    } else {
                        equation = payload + " = " + (log.getResult() == null ? "null" : log.getResult());
                    }
                }
            }

            String line = String.format("[%s] [%s] [%s] PAYLOAD=\"%s\" RESULT=\"%s\" ERROR=\"%s\"%s%n",
                    log.getTimestamp(), log.getTaskId(), log.getTaskType(),
                    log.getPayload(), log.getResult(), log.getError(),
                    equation == null ? "" : " EQUATION=\"" + equation + "\"");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path, true))) {
                writer.write(line);
                writer.flush();
            }
            System.out.print(line);
        } catch (IOException e) {
            System.err.println("Failed to write task log: " + e.getMessage());
        }
    }
}
