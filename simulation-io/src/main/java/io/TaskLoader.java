package io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import Instruction.Instruction;
import java.io.File;
import java.util.List;

public class TaskLoader {
    public List<Instruction> loadFromFile(String filePath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(new File(filePath), new TypeReference<List<Instruction>>() {});
    }
}