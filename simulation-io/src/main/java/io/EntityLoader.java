package io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import entity.Entity;
import java.io.File;
import java.util.List;

public class EntityLoader {
    private final ObjectMapper mapper;

    public EntityLoader() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<Entity> loadFromFile(String filePath) throws Exception {
        return mapper.readValue(new File(filePath), new TypeReference<List<Entity>>() {});
    }
}