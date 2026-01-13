package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import entity.Entity;
import entity.EntityType;
import entity.QC;
import entity.YC;
import entity.IT;
import java.io.File;
import java.util.*;

// 加载json实体
public class EntityLoader {
    private final ObjectMapper objectMapper;

    public EntityLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Entity> loadFromFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("实体文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseEntities(root);
    }

    private List<Entity> parseEntities(JsonNode root) {
        List<Entity> entities = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode entityObj : root) {
                Entity entity = parseEntity(entityObj);
                if (entity != null) {
                    entities.add(entity);
                }
            }
        }
        return entities;
    }

    private Entity parseEntity(JsonNode entityObj) {
        String id = entityObj.get("id").asText();
        String typeStr = entityObj.get("type").asText();
        String initialPosition = entityObj.get("initialPosition").asText();

        EntityType type = parseEntityType(typeStr);
        Map<String, Object> parameters = parseParameters(entityObj.get("parameters"));

        switch (type) {
            case QC:
                return createQC(id, initialPosition, parameters);
            case YC:
                return createYC(id, initialPosition, parameters);
            case IT:
                return createIT(id, initialPosition, parameters);
            default:
                throw new IllegalArgumentException("不支持的实体类型: " + type);
        }
    }

    private EntityType parseEntityType(String typeStr) {
        try {
            return EntityType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的实体类型: " + typeStr);
        }
    }

    private Map<String, Object> parseParameters(JsonNode paramsNode) {
        Map<String, Object> parameters = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                    if (value.isInt()) {
                        parameters.put(entry.getKey(), value.asInt());
                    } else if (value.isDouble()) {
                        parameters.put(entry.getKey(), value.asDouble());
                    } else if (value.isLong()) {
                        parameters.put(entry.getKey(), value.asLong());
                    }
                } else if (value.isBoolean()) {
                    parameters.put(entry.getKey(), value.asBoolean());
                } else if (value.isTextual()) {
                    parameters.put(entry.getKey(), value.asText());
                }
            });
        }
        return parameters;
    }

    private QC createQC(String id, String initialPosition, Map<String, Object> params) {
        // 业务参数
        double maxLiftWeight = getRequiredDoubleParam(params, "maxLiftWeight");
        double spreaderWidth = getRequiredDoubleParam(params, "spreaderWidth");

        // 物理参数
        double maxSpeed = getRequiredDoubleParam(params, "maxSpeed");
        double acceleration = getRequiredDoubleParam(params, "acceleration");
        double deceleration = getRequiredDoubleParam(params, "deceleration");

        // 效率参数
        double baseLiftTime = getRequiredDoubleParam(params, "baseLiftTime");
        double timePerMeter = getRequiredDoubleParam(params, "timePerMeter");

        return new QC(id, initialPosition, maxLiftWeight, spreaderWidth,
                maxSpeed, acceleration, deceleration, baseLiftTime, timePerMeter);
    }

    private YC createYC(String id, String initialPosition, Map<String, Object> params) {
        double maxLiftWeight = getRequiredDoubleParam(params, "maxLiftWeight");

        // 物理参数
        double maxSpeed = getRequiredDoubleParam(params, "maxSpeed");
        double acceleration = getRequiredDoubleParam(params, "acceleration");
        double deceleration = getRequiredDoubleParam(params, "deceleration");

        // 效率参数
        double baseCycleTime = getRequiredDoubleParam(params, "baseCycleTime");
        double timePerTier = getRequiredDoubleParam(params, "timePerTier");

        return new YC(id, initialPosition, maxLiftWeight,
                maxSpeed, acceleration, deceleration, baseCycleTime, timePerTier);
    }

    private IT createIT(String id, String initialPosition, Map<String, Object> params) {
        double maxLoadWeight = getRequiredDoubleParam(params, "maxLoadWeight");

        // 物理参数
        double maxSpeed = getRequiredDoubleParam(params, "maxSpeed");
        double acceleration = getRequiredDoubleParam(params, "acceleration");
        double deceleration = getRequiredDoubleParam(params, "deceleration");

        IT it = new IT(id, initialPosition, maxLoadWeight, maxSpeed, acceleration, deceleration);

    // 布尔值
        Boolean isReeferCapable = getBooleanParam(params, "isReeferCapable");
        if (isReeferCapable != null) {
        }

        Boolean hasTwistLock = getBooleanParam(params, "hasTwistLock");
        if (hasTwistLock != null) {
            // it.setHasTwistLock(hasTwistLock);
        }

        return it;
    }

    private double getRequiredDoubleParam(Map<String, Object> params, String key) {
        if (!params.containsKey(key)) {
            throw new IllegalArgumentException("配置文件错误: 实体参数缺失 '" + key + "'");
        }
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("配置文件错误: 参数 '" + key + "' 必须是数字格式");
    }


    private Boolean getBooleanParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}