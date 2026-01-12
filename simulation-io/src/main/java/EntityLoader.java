import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import entity.Entity;
import entity.EntityType;
import entity.QC;
import entity.YC;
import entity.IT;
import java.io.File;
import java.util.*;

/**
 * 纯粹的JSON实体加载器
 * 只负责从JSON文件加载实体配置
 */
public class EntityLoader {
    private final ObjectMapper objectMapper;

    public EntityLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从JSON文件加载实体
     */
    public List<Entity> loadFromFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("实体文件不存在: " + filePath);
        }

        JsonNode root = objectMapper.readTree(file);
        return parseEntities(root);
    }

    /**
     * 解析实体数组
     */
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

    /**
     * 解析单个实体
     */
    private Entity parseEntity(JsonNode entityObj) {
        String id = entityObj.get("id").asText();
        String typeStr = entityObj.get("type").asText();
        String initialPosition = entityObj.get("initialPosition").asText();

        EntityType type = parseEntityType(typeStr);

        // 解析参数
        Map<String, Object> parameters = parseParameters(entityObj.get("parameters"));

        // 根据类型创建实体
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

    /**
     * 解析实体类型
     */
    private EntityType parseEntityType(String typeStr) {
        try {
            return EntityType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的实体类型: " + typeStr);
        }
    }

    /**
     * 解析参数对象
     */
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

    /**
     * 创建桥吊
     */
    private QC createQC(String id, String initialPosition, Map<String, Object> params) {
        double maxLiftWeight = getDoubleParam(params, "maxLiftWeight", 65.0);
        double spreaderWidth = getDoubleParam(params, "spreaderWidth", 12.0);

        return new QC(id, initialPosition, maxLiftWeight, spreaderWidth);
    }

    /**
     * 创建龙门吊
     */
    private YC createYC(String id, String initialPosition, Map<String, Object> params) {
        double maxLiftWeight = getDoubleParam(params, "maxLiftWeight", 40.0);
        double gantrySpeed = getDoubleParam(params, "gantrySpeed", 1.2);

        return new YC(id, initialPosition, maxLiftWeight, gantrySpeed);
    }

    /**
     * 创建集卡
     */
    private IT createIT(String id, String initialPosition, Map<String, Object> params) {
        double maxLoadWeight = getDoubleParam(params, "maxLoadWeight", 40.0);

        IT it = new IT(id, initialPosition, maxLoadWeight);

        // 设置可选参数
        Boolean isReeferCapable = getBooleanParam(params, "isReeferCapable");
        if (isReeferCapable != null) {
            // 需要在IT类中添加相应方法
        }

        Boolean hasTwistLock = getBooleanParam(params, "hasTwistLock");
        if (hasTwistLock != null) {
            // 需要在IT类中添加相应方法
        }

        return it;
    }

    /**
     * 获取double类型参数
     */
    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * 获取boolean类型参数
     */
    private Boolean getBooleanParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
}