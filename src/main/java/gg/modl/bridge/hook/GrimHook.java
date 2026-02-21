package gg.modl.bridge.hook;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.plugin.BasicGrimPlugin;
import ac.grim.grimac.api.plugin.GrimPlugin;
import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.DetectionSource;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.report.AutoReporter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.UUID;

public class GrimHook implements AntiCheatHook {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final ViolationTracker violationTracker;
    private final AutoReporter autoReporter;
    private GrimAbstractAPI grimApi;

    public GrimHook(JavaPlugin plugin, BridgeConfig config, ViolationTracker violationTracker, AutoReporter autoReporter) {
        this.plugin = plugin;
        this.config = config;
        this.violationTracker = violationTracker;
        this.autoReporter = autoReporter;
    }

    @Override
    public String getName() {
        return "GrimAC";
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("GrimAC") != null;
    }

    @Override
    public void register() {
        try {
            grimApi = Bukkit.getServicesManager().getRegistration(GrimAbstractAPI.class).getProvider();
            GrimPlugin grimPlugin = new BasicGrimPlugin(
                    plugin.getLogger(),
                    plugin.getDataFolder(),
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    Collections.emptyList()
            );
            grimApi.getEventBus().subscribe(grimPlugin, FlagEvent.class, this::onFlag);
            plugin.getLogger().info("[ModlBridge] Hooked into GrimAC");
        } catch (Exception e) {
            plugin.getLogger().warning("[ModlBridge] Failed to hook into GrimAC: " + e.getMessage());
        }
    }

    @Override
    public void unregister() {
        // GrimAC event bus does not require explicit unsubscription; plugin disable handles it
    }

    private void onFlag(FlagEvent event) {
        try {
            if (event.isCancelled()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[ModlBridge] [DEBUG] GrimAC FlagEvent cancelled for check: "
                            + event.getCheck().getCheckName() + " player: " + event.getPlayer().getName());
                }
                return;
            }

            GrimUser user = event.getPlayer();
            UUID uuid = user.getUniqueId();
            String playerName = user.getName();
            AbstractCheck check = event.getCheck();
            String checkName = check.getCheckName();
            String verbose = event.getVerbose();

            if (config.isDebug()) {
                int currentCount = violationTracker.getViolationCount(uuid, DetectionSource.GRIM, checkName);
                plugin.getLogger().info("[ModlBridge] [DEBUG] GrimAC flag: player=" + playerName
                        + " check=" + checkName + " currentVL=" + (currentCount + 1)
                        + " threshold=" + config.getReportViolationThreshold(checkName)
                        + " verbose=" + verbose);
            }

            violationTracker.addViolation(uuid, DetectionSource.GRIM, checkName, verbose);
            autoReporter.checkAndReport(uuid, playerName, DetectionSource.GRIM, checkName);
        } catch (Exception e) {
            plugin.getLogger().warning("[ModlBridge] Error processing GrimAC flag event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
