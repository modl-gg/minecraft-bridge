package gg.modl.bridge.hook;

import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.DetectionSource;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.report.AutoReporter;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.user.event.DetectionAlertEvent;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class PolarHook implements AntiCheatHook {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final ViolationTracker violationTracker;
    private final AutoReporter autoReporter;
    private PolarApi polarApi;

    public PolarHook(JavaPlugin plugin, BridgeConfig config, ViolationTracker violationTracker, AutoReporter autoReporter) {
        this.plugin = plugin;
        this.config = config;
        this.violationTracker = violationTracker;
        this.autoReporter = autoReporter;
    }

    @Override
    public String getName() {
        return "Polar";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("top.polar.api.PolarApiAccessor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void register() {
        try {
            WeakReference<PolarApi> ref = PolarApiAccessor.access();
            polarApi = ref.get();
            if (polarApi == null) {
                plugin.getLogger().warning("[ModlBridge] Polar API reference was null");
                return;
            }
            polarApi.events().repository().registerListener(
                    DetectionAlertEvent.class,
                    this::onDetection
            );
            plugin.getLogger().info("[ModlBridge] Hooked into Polar");
        } catch (Exception e) {
            plugin.getLogger().warning("[ModlBridge] Failed to hook into Polar: " + e.getMessage());
        }
    }

    @Override
    public void unregister() {
        // Polar event system does not require explicit unregistration
    }

    private void onDetection(DetectionAlertEvent event) {
        try {
            UUID uuid = event.user().uuid();
            String playerName = event.user().username();
            String checkName = event.check().type().name();
            String verbose = event.details();

            if (config.isDebug()) {
                int currentCount = violationTracker.getViolationCount(uuid, DetectionSource.POLAR, checkName);
                plugin.getLogger().info("[ModlBridge] [DEBUG] Polar detection: player=" + playerName
                        + " check=" + checkName + " currentVL=" + (currentCount + 1)
                        + " threshold=" + config.getReportViolationThreshold(checkName)
                        + " details=" + verbose);
            }

            violationTracker.addViolation(uuid, DetectionSource.POLAR, checkName, verbose);
            autoReporter.checkAndReport(uuid, playerName, DetectionSource.POLAR, checkName);
        } catch (Exception e) {
            plugin.getLogger().warning("[ModlBridge] Error processing Polar detection event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
