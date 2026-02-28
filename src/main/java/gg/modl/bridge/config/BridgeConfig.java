package gg.modl.bridge.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

public class BridgeConfig {

    private final FileConfiguration config;

    public BridgeConfig(FileConfiguration config) {
        this.config = config;
    }

    private static final String PRODUCTION_URL = "https://api.modl.gg/v1";
    private static final String TESTING_URL = "https://api.modl.top/v1";

    public String getBaseUrl() {
        return isTestingMode() ? TESTING_URL : PRODUCTION_URL;
    }

    public String getApiKey() {
        return config.getString("api-key", "");
    }

    public String getServerDomain() {
        return config.getString("server-domain", "");
    }

    public boolean isTestingMode() {
        return config.getBoolean("testing-mode", false);
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    public String getIssuerName() {
        return config.getString("issuer-name", "Anti-cheat");
    }

    public String getAnticheatName() {
        return config.getString("anticheat-name", "Anti-cheat");
    }

    public String getServerName() {
        return config.getString("server-name", "Server 1");
    }

    public int getBanTypeOrdinal() {
        return config.getInt("ban-type-ordinal", 14);
    }

    public int getKickTypeOrdinal() {
        return config.getInt("kick-type-ordinal", 0);
    }

    public int getReportCooldown() {
        return config.getInt("report-cooldown", 60);
    }

    public int getReportViolationThreshold(String checkName) {
        String checkPath = "report-violation-threshold.checks." + checkName.toLowerCase();
        if (config.contains(checkPath)) {
            return config.getInt(checkPath);
        }
        return config.getInt("report-violation-threshold.default", 10);
    }

    public List<String> getStatWipeCommands() {
        List<String> commands = config.getStringList("stat-wipe-commands");
        return commands != null ? commands : Collections.emptyList();
    }

    public boolean isValid() {
        String apiKey = getApiKey();
        String serverDomain = getServerDomain();
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            return false;
        }
        return serverDomain != null && !serverDomain.isEmpty() && !serverDomain.equals("yourserver.modl.gg");
    }
}
