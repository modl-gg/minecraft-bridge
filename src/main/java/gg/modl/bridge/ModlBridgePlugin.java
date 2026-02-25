package gg.modl.bridge;

import gg.modl.bridge.command.AnticheatPunishCommand;
import gg.modl.bridge.command.AnticheatReportCommand;
import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.hook.AntiCheatHook;
import gg.modl.bridge.hook.GrimHook;
import gg.modl.bridge.hook.PolarHook;
import gg.modl.bridge.http.BridgeHttpClient;
import gg.modl.bridge.report.AutoReporter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ModlBridgePlugin extends JavaPlugin implements Listener {

    private BridgeConfig bridgeConfig;
    private BridgeHttpClient httpClient;
    private ViolationTracker violationTracker;
    private AutoReporter autoReporter;
    private final List<AntiCheatHook> hooks = new ArrayList<>();
    private boolean polarAvailable = false;
    private boolean grimAvailable = false;

    @Override
    public void onLoad() {
        // Check for Polar early — it requires registration during onLoad via LoaderApi
        try {
            Class.forName("top.polar.api.loader.LoaderApi");
            polarAvailable = true;
            top.polar.api.loader.LoaderApi.registerEnableCallback(() -> {
                if (isEnabled()) {
                    hookPolar();
                }
            });
            getLogger().info("[ModlBridge] Polar detected, registered enable callback");
        } catch (ClassNotFoundException ignored) {
            // Polar not present
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bridgeConfig = new BridgeConfig(getConfig());

        if (!bridgeConfig.isValid()) {
            getLogger().severe("[ModlBridge] Invalid configuration! Please set your api-key and server-domain in config.yml");
            getLogger().severe("[ModlBridge] Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        httpClient = new BridgeHttpClient(
                bridgeConfig.getBaseUrl(),
                bridgeConfig.getApiKey(),
                bridgeConfig.getServerDomain(),
                bridgeConfig.isDebug(),
                getLogger()
        );

        violationTracker = new ViolationTracker();
        violationTracker.startCleanupTask(this);

        autoReporter = new AutoReporter(this, bridgeConfig, httpClient, violationTracker);

        // Register quit listener to clean up violation data
        getServer().getPluginManager().registerEvents(this, this);

        // Register console commands
        AnticheatPunishCommand punishCommand = new AnticheatPunishCommand(this, bridgeConfig, httpClient);
        getCommand("anticheat-ban").setExecutor(punishCommand);
        getCommand("anticheat-kick").setExecutor(punishCommand);

        AnticheatReportCommand reportCommand = new AnticheatReportCommand(this, bridgeConfig, httpClient, violationTracker);
        getCommand("anticheat-report").setExecutor(reportCommand);

        grimAvailable = Bukkit.getPluginManager().getPlugin("GrimAC") != null;

        // Hook into GrimAC if available
        if (grimAvailable) {
            GrimHook grimHook = new GrimHook(this, bridgeConfig, violationTracker, autoReporter);
            grimHook.register();
            hooks.add(grimHook);
        }

        // Hook into Polar if available (non-LoaderApi path, e.g. if Polar loaded before us)
        if (!polarAvailable) {
            PolarHook polarHook = new PolarHook(this, bridgeConfig, violationTracker, autoReporter);
            if (polarHook.isAvailable()) {
                polarHook.register();
                hooks.add(polarHook);
            }
        }

        if (hooks.isEmpty() && !polarAvailable) {
            getLogger().warning("[ModlBridge] No anticheat plugins detected! Install GrimAC or Polar for the bridge to function.");
        }

        getLogger().info("[ModlBridge] Enabled with " + hooks.size() + " anticheat hook(s)" + (polarAvailable ? " (Polar pending callback)" : ""));

        if (bridgeConfig.isDebug()) {
            getLogger().info("[ModlBridge] [DEBUG] Config: defaultThreshold=" + bridgeConfig.getReportViolationThreshold("_")
                    + " cooldown=" + bridgeConfig.getReportCooldown() + "s"
                    + " api=" + bridgeConfig.getBaseUrl()
                    + " domain=" + bridgeConfig.getServerDomain());
        }
    }

    @Override
    public void onDisable() {
        if (violationTracker != null) {
            violationTracker.stopCleanupTask();
        }

        for (AntiCheatHook hook : hooks) {
            hook.unregister();
        }
        hooks.clear();

        if (httpClient != null) {
            httpClient.shutdown();
        }

        getLogger().info("[ModlBridge] Disabled");
    }

    private void hookPolar() {
        if (violationTracker == null || autoReporter == null) {
            getLogger().warning("[ModlBridge] Polar enable callback fired but plugin is not fully initialized");
            return;
        }

        PolarHook polarHook = new PolarHook(this, bridgeConfig, violationTracker, autoReporter);
        polarHook.register();
        hooks.add(polarHook);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (violationTracker != null) {
            violationTracker.resetPlayer(event.getPlayer().getUniqueId());
        }
        if (autoReporter != null) {
            autoReporter.clearCooldown(event.getPlayer().getUniqueId());
        }
    }
}
