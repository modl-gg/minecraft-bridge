package gg.modl.bridge.action;

import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.DetectionSource;
import gg.modl.bridge.detection.ViolationRecord;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.http.BridgeHttpClient;
import gg.modl.bridge.http.request.CreatePunishmentRequest;
import gg.modl.bridge.http.request.CreateTicketRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ActionExecutor {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final BridgeHttpClient httpClient;
    private final ViolationTracker violationTracker;
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    public ActionExecutor(JavaPlugin plugin, BridgeConfig config, BridgeHttpClient httpClient, ViolationTracker violationTracker) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = httpClient;
        this.violationTracker = violationTracker;
    }

    public void handleViolation(UUID uuid, String playerName, DetectionSource source, String checkName, double accumulatedVL, String verbose) {
        String sourceName = source.name().toLowerCase();

        if (!config.isCheckEnabled(sourceName, checkName)) {
            return;
        }

        int reportThreshold = config.getCheckReportThreshold(sourceName, checkName);
        int punishThreshold = config.getCheckPunishThreshold(sourceName, checkName);

        String compositeKey = ViolationTracker.compositeKey(source, checkName);

        if (config.isDebug()) {
            plugin.getLogger().info("[Debug] " + playerName + " | " + compositeKey + " | VL: " + String.format("%.1f", accumulatedVL)
                    + " | Report@" + reportThreshold + " Punish@" + punishThreshold + " | " + verbose);
        }

        // Report threshold check
        if (reportThreshold > 0 && accumulatedVL >= reportThreshold) {
            String reportCooldownKey = uuid + ":" + compositeKey + ":report";
            if (isCooldownElapsed(reportCooldownKey, config.getReportCooldown())) {
                setCooldown(reportCooldownKey);
                submitReport(uuid, playerName, source, checkName, accumulatedVL);
            }
        }

        // Punishment threshold check
        if (punishThreshold > 0 && accumulatedVL >= punishThreshold) {
            String punishCooldownKey = uuid + ":" + compositeKey + ":punishment";
            if (isCooldownElapsed(punishCooldownKey, config.getPunishmentCooldown())) {
                setCooldown(punishCooldownKey);
                submitPunishment(uuid, playerName, source, checkName, accumulatedVL);
            }
        }
    }

    private void submitReport(UUID uuid, String playerName, DetectionSource source, String checkName, double vl) {
        String subject = "[" + source.name() + "] " + checkName + " - " + playerName + " (VL: " + String.format("%.0f", vl) + ")";

        List<ViolationRecord> records = violationTracker.getRecords(uuid, source, checkName);
        String description = "Automated anticheat report.\n\nRecent violations:\n" +
                records.stream()
                        .map(r -> "- " + r.toString())
                        .collect(Collectors.joining("\n"));

        List<String> tags = List.of("anticheat", source.name().toLowerCase(), checkName.toLowerCase());

        CreateTicketRequest request = new CreateTicketRequest(
                uuid.toString(),
                config.getIssuerName(),
                "REPORT",
                subject,
                description,
                uuid.toString(),
                playerName,
                tags,
                "NORMAL",
                config.getServerName()
        );

        httpClient.createTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Report created for " + playerName + " (" + checkName + ") - Ticket: " + response.getTicketId());
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to create report for " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error creating report for " + playerName + ": " + throwable.getMessage());
            return null;
        });
    }

    private void submitPunishment(UUID uuid, String playerName, DetectionSource source, String checkName, double vl) {
        String sourceName = source.name().toLowerCase();

        String reason = config.getCheckPunishReason(sourceName, checkName)
                .replace("{source}", source.name())
                .replace("{check}", checkName)
                .replace("{vl}", String.format("%.0f", vl));

        String action = config.getCheckPunishAction(sourceName, checkName);
        long duration = config.getCheckPunishDuration(sourceName, checkName);
        Long durationValue = duration < 0 ? null : duration;

        List<String> notes = List.of(
                "Automated punishment by ModlBridge",
                "Source: " + source.name() + " | Check: " + checkName + " | VL: " + String.format("%.0f", vl)
        );

        CreatePunishmentRequest request = new CreatePunishmentRequest(
                uuid.toString(),
                config.getIssuerName(),
                config.getDefaultPunishTypeOrdinal(),
                reason,
                durationValue,
                null,
                notes,
                null,
                config.getDefaultPunishSeverity(),
                "ACTIVE"
        );

        httpClient.createPunishment(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Punishment created for " + playerName + " (" + checkName + ") - ID: " + response.getPunishmentId());

                // Execute kick locally if configured
                if ("kick".equalsIgnoreCase(action) && config.executeKicksLocally()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.kickPlayer(reason);
                        }
                    });
                }

                // Reset VL for this check after punishment
                violationTracker.resetCheck(uuid, source, checkName);
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to create punishment for " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error creating punishment for " + playerName + ": " + throwable.getMessage());
            return null;
        });
    }

    private boolean isCooldownElapsed(String key, int cooldownSeconds) {
        Long lastTime = cooldowns.get(key);
        if (lastTime == null) return true;
        return (System.currentTimeMillis() - lastTime) >= (cooldownSeconds * 1000L);
    }

    private void setCooldown(String key) {
        cooldowns.put(key, System.currentTimeMillis());
    }
}
