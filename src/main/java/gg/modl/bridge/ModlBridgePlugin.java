package gg.modl.bridge;

import gg.modl.bridge.action.ActionExecutor;
import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.hook.AntiCheatHook;
import gg.modl.bridge.hook.GrimHook;
import gg.modl.bridge.hook.PolarHook;
import gg.modl.bridge.http.BridgeHttpClient;
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
    private ActionExecutor actionExecutor;
    private final List<AntiCheatHook> hooks = new ArrayList<>();
    private boolean polarAvailable = false;

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
            getLogger().severe("[ModlBridge] Invalid configuration! Please set your api-key, base-url, and server-domain in config.yml");
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

        violationTracker = new ViolationTracker(bridgeConfig);
        violationTracker.startDecayTask(this);

        actionExecutor = new ActionExecutor(this, bridgeConfig, httpClient, violationTracker);

        // Register quit listener to clean up violation data
        getServer().getPluginManager().registerEvents(this, this);

        // Hook into GrimAC if available
        GrimHook grimHook = new GrimHook(this, violationTracker, actionExecutor);
        if (grimHook.isAvailable()) {
            grimHook.register();
            hooks.add(grimHook);
        }

        // Hook into Polar if available (non-LoaderApi path, e.g. if Polar loaded before us)
        if (!polarAvailable) {
            PolarHook polarHook = new PolarHook(this, violationTracker, actionExecutor);
            if (polarHook.isAvailable()) {
                polarHook.register();
                hooks.add(polarHook);
            }
        }

        if (hooks.isEmpty() && !polarAvailable) {
            getLogger().warning("[ModlBridge] No anticheat plugins detected! Install GrimAC or Polar for the bridge to function.");
        }

        getLogger().info("[ModlBridge] Enabled with " + hooks.size() + " anticheat hook(s)" + (polarAvailable ? " (Polar pending callback)" : ""));
    }

    @Override
    public void onDisable() {
        if (violationTracker != null) {
            violationTracker.stopDecayTask();
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
        if (violationTracker == null || actionExecutor == null) {
            getLogger().warning("[ModlBridge] Polar enable callback fired but plugin is not fully initialized");
            return;
        }

        PolarHook polarHook = new PolarHook(this, violationTracker, actionExecutor);
        polarHook.register();
        hooks.add(polarHook);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (violationTracker != null) {
            violationTracker.resetPlayer(event.getPlayer().getUniqueId());
        }
    }
}
