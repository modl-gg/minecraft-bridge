package gg.modl.bridge.report;

import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.DetectionSource;
import gg.modl.bridge.detection.ViolationRecord;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.http.BridgeHttpClient;
import gg.modl.bridge.http.request.CreateTicketRequest;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AutoReporter {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final BridgeHttpClient httpClient;
    private final ViolationTracker violationTracker;
    private final ConcurrentHashMap<UUID, Long> reportCooldowns = new ConcurrentHashMap<>();

    public AutoReporter(JavaPlugin plugin, BridgeConfig config, BridgeHttpClient httpClient, ViolationTracker violationTracker) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = httpClient;
        this.violationTracker = violationTracker;
    }

    public void checkAndReport(UUID uuid, String playerName, DetectionSource source, String checkName) {
        int vl = violationTracker.getViolationCount(uuid, source, checkName);
        int threshold = config.getReportViolationThreshold(checkName);

        if (config.isDebug()) {
            plugin.getLogger().info("[ModlBridge] [DEBUG] checkAndReport: player=" + playerName
                    + " source=" + source.name() + " check=" + checkName
                    + " vl=" + vl + " threshold=" + threshold);
        }

        if (vl < threshold) {
            return;
        }

        Long lastReport = reportCooldowns.get(uuid);
        long now = System.currentTimeMillis();
        if (lastReport != null && (now - lastReport) < config.getReportCooldown() * 1000L) {
            if (config.isDebug()) {
                long remaining = (config.getReportCooldown() * 1000L) - (now - lastReport);
                plugin.getLogger().info("[ModlBridge] [DEBUG] Report blocked by cooldown for " + playerName
                        + " (" + (remaining / 1000) + "s remaining)");
            }
            return;
        }

        reportCooldowns.put(uuid, now);
        submitReport(uuid, playerName, source, checkName, vl);
    }

    private void submitReport(UUID uuid, String playerName, DetectionSource source, String checkName, int vl) {
        List<ViolationRecord> records = violationTracker.getRecords(uuid);

        String subject = "[" + config.getAnticheatName() + "] " + checkName + " - " + playerName + " (VL: " + vl + ")";

        String description = "**Automated anticheat report.**\n\n" +
                "**Player:** " + playerName + "\n\n" +
                "**Trigger:** " + source.name() + " " + checkName + " (VL: " + vl + ")\n\n" +
                "**Recent violations:**\n```\n" +
                records.stream()
                        .map(ViolationRecord::toString)
                        .collect(Collectors.joining("\n")) +
                "\n```";

        List<String> tags = List.of("automated");

        CreateTicketRequest request = new CreateTicketRequest(
                uuid.toString(),
                config.getIssuerName(),
                "player",
                subject,
                description,
                uuid.toString(),
                playerName,
                tags,
                "NORMAL",
                config.getServerName()
        );

        plugin.getLogger().info("[ModlBridge] Auto-report triggered for " + playerName + " (" + source.name() + " " + checkName + " VL: " + vl + ")");

        httpClient.createTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Report created for " + playerName + " - Ticket: " + response.getTicketId());
                // Clear reported violations so they don't appear in the next report
                violationTracker.resetPlayer(uuid);
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to create report for " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error creating report for " + playerName + ": " + throwable.getMessage());
            return null;
        });
    }

    public void clearCooldown(UUID uuid) {
        reportCooldowns.remove(uuid);
    }
}
