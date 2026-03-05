package gg.modl.bridge.locale;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class BridgeLocaleManager {

    private static final Pattern MINIMESSAGE_TAG_PATTERN = Pattern.compile("<[a-zA-Z_/!#][a-zA-Z0-9_:/.#-]*>");

    private Map<String, Object> messages = Collections.emptyMap();
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer sectionSerializer;

    public BridgeLocaleManager(Logger logger) {
        this.logger = logger;
        this.miniMessage = MiniMessage.builder().strict(false).build();
        this.sectionSerializer = LegacyComponentSerializer.legacySection();
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
     * Check if text contains MiniMessage tags.
     */
    public static boolean isMiniMessage(String text) {
        if (text == null || text.isEmpty()) return false;
        return MINIMESSAGE_TAG_PATTERN.matcher(text).find();
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
        return colorize(raw);
    }

    /**
     * Get a message by dot-path key with no placeholders.
     */
    public String getMessage(String key) {
        return getMessage(key, null);
    }

    /**
     * Colorize a string, auto-detecting MiniMessage or legacy &amp; codes.
     * Returns a legacy §-encoded string suitable for Bukkit APIs.
     */
    public String colorize(String text) {
        if (text == null || text.isEmpty()) return text;
        if (isMiniMessage(text)) {
            return sectionSerializer.serialize(miniMessage.deserialize(text));
        }
        return ChatColor.translateAlternateColorCodes('&', text);
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
