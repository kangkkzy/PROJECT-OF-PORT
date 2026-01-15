package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import event.SimEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LogWriter {
    private final ObjectMapper objectMapper;

    public LogWriter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void writeLog(List<SimEvent> events, String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) return;
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) Files.createDirectories(path);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File file = path.resolve("simulation_log_" + timestamp + ".json").toFile();
            objectMapper.writeValue(file, events);
            System.out.println(">>> [成功] 仿真日志已保存: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}