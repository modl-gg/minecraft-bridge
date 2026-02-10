package gg.modl.bridge.hook;

import gg.modl.bridge.action.ActionExecutor;
import gg.modl.bridge.detection.DetectionSource;
import gg.modl.bridge.detection.ViolationTracker;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.user.event.DetectionAlertEvent;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class PolarHook implements AntiCheatHook {

    private final JavaPlugin plugin;
    private final ViolationTracker violationTracker;
    private final ActionExecutor actionExecutor;
    private PolarApi polarApi;

    public PolarHook(JavaPlugin plugin, ViolationTracker violationTracker, ActionExecutor actionExecutor) {
        this.plugin = plugin;
        this.violationTracker = violationTracker;
        this.actionExecutor = actionExecutor;
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
        UUID uuid = event.user().uuid();
        String playerName = event.user().username();
        String checkName = event.check().type().name();
        String verbose = event.details();

        double accumulatedVL = violationTracker.addViolation(uuid, DetectionSource.POLAR, checkName, verbose);
        actionExecutor.handleViolation(uuid, playerName, DetectionSource.POLAR, checkName, accumulatedVL, verbose);
    }
}
