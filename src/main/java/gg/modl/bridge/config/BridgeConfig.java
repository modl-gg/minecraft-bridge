package gg.modl.bridge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class BridgeConfig {

    private final FileConfiguration config;

    public BridgeConfig(FileConfiguration config) {
        this.config = config;
    }

    // API settings
    public String getBaseUrl() {
        return config.getString("api.base-url", "https://api.modl.gg/v1");
    }

    public String getApiKey() {
        return config.getString("api.api-key", "");
    }

    public String getServerDomain() {
        return config.getString("api.server-domain", "");
    }

    public boolean isDebug() {
        return config.getBoolean("api.debug", false);
    }

    // General settings
    public String getIssuerName() {
        return config.getString("issuer-name", "Anticheat");
    }

    public String getServerName() {
        return config.getString("server-name", "Server 1");
    }

    // Default thresholds
    public int getDefaultReportThreshold() {
        return config.getInt("defaults.report-threshold", 20);
    }

    public int getDefaultPunishThreshold() {
        return config.getInt("defaults.punish-threshold", 40);
    }

    public String getDefaultPunishAction() {
        return config.getString("defaults.punish-action", "ban");
    }

    public long getDefaultPunishDuration() {
        return config.getLong("defaults.punish-duration", 604800L);
    }

    public int getDefaultPunishTypeOrdinal() {
        return config.getInt("defaults.punish-type-ordinal", 14);
    }

    public String getDefaultPunishReason() {
        return config.getString("defaults.punish-reason", "Unfair Advantage - {source} {check}");
    }

    public String getDefaultPunishSeverity() {
        return config.getString("defaults.punish-severity", "HIGH");
    }

    // Decay settings
    public int getDecayInterval() {
        return config.getInt("decay.interval", 30);
    }

    public double getDecayAmount() {
        return config.getDouble("decay.amount", 2.0);
    }

    // Cooldown settings
    public int getReportCooldown() {
        return config.getInt("cooldowns.report", 120);
    }

    public int getPunishmentCooldown() {
        return config.getInt("cooldowns.punishment", 300);
    }

    // Per-check config
    public boolean isCheckEnabled(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase() + ".enabled";
        return config.getBoolean(path, true);
    }

    public int getCheckReportThreshold(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase() + ".report-threshold";
        return config.getInt(path, getDefaultReportThreshold());
    }

    public int getCheckPunishThreshold(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase() + ".punish-threshold";
        return config.getInt(path, getDefaultPunishThreshold());
    }

    public String getCheckPunishAction(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase() + ".punish-action";
        return config.getString(path, getDefaultPunishAction());
    }

    public long getCheckPunishDuration(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase() + ".punish-duration";
        return config.getLong(path, getDefaultPunishDuration());
    }

    public String getCheckPunishReason(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase() + ".punish-reason";
        return config.getString(path, getDefaultPunishReason());
    }

    public boolean hasCheckSection(String source, String checkName) {
        String path = "checks." + source.toLowerCase() + "." + checkName.toLowerCase();
        return config.isConfigurationSection(path);
    }

    public boolean executeKicksLocally() {
        return config.getBoolean("execute-kicks-locally", true);
    }

    public boolean isValid() {
        String apiKey = getApiKey();
        String baseUrl = getBaseUrl();
        String serverDomain = getServerDomain();

        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            return false;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            return false;
        }
        if (serverDomain == null || serverDomain.isEmpty() || serverDomain.equals("yourserver.modl.gg")) {
            return false;
        }
        return true;
    }
}
