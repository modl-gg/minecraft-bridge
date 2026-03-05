package gg.modl.bridge.locale;

import org.bukkit.ChatColor;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class BridgeLocaleManager {

    private Map<String, Object> messages = Collections.emptyMap();
    private final Logger logger;

    public BridgeLocaleManager(Logger logger) {
        this.logger = logger;
        load();
    }

    private void load() {
        try (InputStream is = getClass().getResourceAsStream("/locale/en_US.yml")) {
            if (is == null) {
                logger.warning("[BridgeLocale] Could not find /locale/en_US.yml in resources");
                return;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);
            if (data != null) {
                messages = data;
            }
        } catch (Exception e) {
            logger.warning("[BridgeLocale] Failed to load locale file: " + e.getMessage());
        }
    }

    /**
     * Get a message by dot-path key with placeholder replacement.
     *
     * @param key          dot-separated path (e.g. "staff_mode.target.cleared")
     * @param placeholders pairs of placeholder name and value (e.g. "player", "Steve")
     * @return colorized message, or the key if not found
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String raw = resolve(key);
        if (raw == null) {
            return key;
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Get a message by dot-path key with no placeholders.
     */
    public String getMessage(String key) {
        return getMessage(key, null);
    }

    @SuppressWarnings("unchecked")
    private String resolve(String key) {
        String[] parts = key.split("\\.");
        Object current = messages;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current instanceof String ? (String) current : null;
    }
}
