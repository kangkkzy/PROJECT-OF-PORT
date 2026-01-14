package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
        // 开启缩进，让生成的 JSON 易于阅读
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void writeLog(List<SimEvent> events, String directoryPath) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            System.err.println(">>> [警告] 日志输出目录未配置，跳过保存。");
            return;
        }

        try {
            // 1. 确保目录存在
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println(">>> [系统] 自动创建日志目录: " + path.toAbsolutePath());
            }

            // 2. 生成带时间戳的文件名，例如: simulation_log_20231027_103055.json
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "simulation_log_" + timestamp + ".json";
            File file = path.resolve(fileName).toFile();

            // 3. 写入文件
            System.out.println(">>> [系统] 正在写入仿真日志 (" + events.size() + " 条记录)...");
            objectMapper.writeValue(file, events);
            System.out.println(">>> [成功] 仿真日志已落库: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println(">>> [错误] 保存仿真日志失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}