package gg.modl.bridge;

import gg.modl.bridge.command.ProxyCmdCommand;
import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.config.StaffModeConfig;
import gg.modl.bridge.reporter.detection.ViolationTracker;
import gg.modl.bridge.handler.FreezeHandler;
import gg.modl.bridge.handler.StaffModeHandler;
import gg.modl.bridge.reporter.hook.AntiCheatHook;
import gg.modl.bridge.reporter.hook.GrimHook;
import gg.modl.bridge.reporter.hook.PolarHook;
import gg.modl.bridge.locale.BridgeLocaleManager;
import gg.modl.bridge.query.BridgeQueryServer;
import gg.modl.bridge.reporter.AutoReporter;
import gg.modl.bridge.reporter.TicketCreator;
import gg.modl.bridge.statwipe.StatWipeHandler;
import gg.modl.bridge.util.YamlMergeUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModlBridgePlugin extends JavaPlugin implements Listener {
    private BridgeConfig bridgeConfig;
    private ViolationTracker violationTracker;
    private AutoReporter autoReporter;
    private StatWipeHandler statWipeHandler;
    private FreezeHandler freezeHandler;
    private StaffModeHandler staffModeHandler;
    private BridgeQueryServer queryServer;
    private BridgeLocaleManager localeManager;
    private final List<AntiCheatHook> hooks = new ArrayList<>();
    private boolean polarAvailable;

    @Override
    public void onLoad() {
        try {
            Class.forName("top.polar.api.loader.LoaderApi");
            polarAvailable = true;
            top.polar.api.loader.LoaderApi.registerEnableCallback(() -> {
                if (isEnabled()) {
                    hookPolar();
                }
            });
            getLogger().info("Polar detected, registered enable callback");
        } catch (ClassNotFoundException ignored) {}
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeDefaultConfigs();
        reloadConfig();

        bridgeConfig = new BridgeConfig(getConfig());
        localeManager = new BridgeLocaleManager(getLogger());

        if (!bridgeConfig.isValid()) {
            getLogger().severe("Invalid configuration! Please set your api-key in config.yml");
            getLogger().severe("Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        violationTracker = new ViolationTracker();
        violationTracker.startCleanupTask(this);
        statWipeHandler = new StatWipeHandler(this, bridgeConfig);

        freezeHandler = new FreezeHandler(this, localeManager);
        freezeHandler.register();

        StaffModeConfig staffModeConfig = new StaffModeConfig(this);
        staffModeHandler = new StaffModeHandler(this, bridgeConfig, freezeHandler, localeManager, staffModeConfig);
        staffModeHandler.register();
        freezeHandler.setStaffModeHandler(staffModeHandler);

        if (bridgeConfig.isQueryEnabled()) {
            queryServer = new BridgeQueryServer(
                    bridgeConfig.getQueryPort(),
                    bridgeConfig.getApiKey(),
                    statWipeHandler,
                    freezeHandler,
                    staffModeHandler,
                    this
            );
            queryServer.start();
            staffModeHandler.setQueryServer(queryServer);
            freezeHandler.setQueryServer(queryServer);
        }

        autoReporter = new AutoReporter(this, bridgeConfig, buildTicketCreator(), violationTracker);
        getServer().getPluginManager().registerEvents(this, this);

        registerAntiCheatHooks();

        if (queryServer != null) {
            getCommand("proxycmd").setExecutor(new ProxyCmdCommand(this, localeManager, queryServer));
        }

        getLogger().info("Enabled with " + hooks.size() + " anticheat hook(s)"
                + (polarAvailable ? " (Polar pending callback)" : ""));

        if (bridgeConfig.isDebug()) {
            getLogger().info("[DEBUG] Config: defaultThreshold=" + bridgeConfig.getReportViolationThreshold("_")
                    + " cooldown=" + bridgeConfig.getReportCooldown() + "s");
        }
    }

    @Override
    public void onDisable() {
        if (staffModeHandler != null) staffModeHandler.shutdown();
        if (queryServer != null) queryServer.shutdown();
        if (violationTracker != null) violationTracker.stopCleanupTask();

        hooks.forEach(AntiCheatHook::unregister);
        hooks.clear();

        getLogger().info("Disabled");
    }

    private TicketCreator buildTicketCreator() {
        Plugin modlPlugin = Bukkit.getPluginManager().getPlugin("modl");
        if (modlPlugin != null && modlPlugin.isEnabled()) {
            try {
                Method method = modlPlugin.getClass().getMethod("createTicketFromBridge",
                        String.class, String.class, String.class,
                        String.class, String.class,
                        String.class, String.class,
                        String.class, String.class, String.class);
                getLogger().info("modl plugin detected on same server, using direct reflection for tickets");
                return (creatorUuid, creatorName, type, subject, description,
                        reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer) -> {
                    try {
                        method.invoke(modlPlugin, creatorUuid, creatorName, type,
                                subject, description, reportedPlayerUuid, reportedPlayerName,
                                tagsJoined, priority, createdServer);
                    } catch (Exception e) {
                        getLogger().warning("Failed to create ticket via reflection: " + e.getMessage());
                    }
                };
            } catch (NoSuchMethodException e) {
                getLogger().warning("modl plugin found but missing createTicketFromBridge method (outdated version?)");
            }
        }

        getLogger().info("Using TCP bridge for ticket creation");
        return (creatorUuid, creatorName, type, subject, description,
                reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer) -> {
            queryServer.sendToAllClients("CREATE_REPORT",
                    creatorUuid, creatorName, type, subject, description,
                    reportedPlayerUuid, reportedPlayerName, tagsJoined, priority, createdServer);
        };
    }

    private void registerAntiCheatHooks() {
        if (Bukkit.getPluginManager().getPlugin("GrimAC") != null) {
            GrimHook grimHook = new GrimHook(this, bridgeConfig, violationTracker, autoReporter);
            grimHook.register();
            hooks.add(grimHook);
        }

        if (!polarAvailable) {
            PolarHook polarHook = new PolarHook(this, bridgeConfig, violationTracker, autoReporter);
            if (polarHook.isAvailable()) {
                polarHook.register();
                hooks.add(polarHook);
            }
        }

        if (hooks.isEmpty() && !polarAvailable) {
            getLogger().warning("No anticheat plugins detected! Install GrimAC or Polar for the bridge to function.");
        }
    }

    private void mergeDefaultConfigs() {
        Path dataPath = getDataFolder().toPath();
        YamlMergeUtil.mergeWithDefaults("/config.yml", dataPath.resolve("config.yml"), getLogger());
        YamlMergeUtil.mergeWithDefaults("/staff_mode.yml", dataPath.resolve("staff_mode.yml"), getLogger());
    }

    private void hookPolar() {
        if (violationTracker == null || autoReporter == null) {
            getLogger().warning("Polar enable callback fired but plugin is not fully initialized");
            return;
        }

        PolarHook polarHook = new PolarHook(this, bridgeConfig, violationTracker, autoReporter);
        polarHook.register();
        hooks.add(polarHook);
    }

    /**
     * Execute stat-wipe commands for a player. Called by the modl plugin via reflection
     * (same-server setup).
     *
     * @param username     the player's username
     * @param punishmentId the punishment ID for logging
     * @return true if all commands executed successfully
     */
    public boolean executeStatWipeCommands(String username, String punishmentId) {
        if (statWipeHandler == null) {
            getLogger().warning("Stat wipe handler not initialized");
            return false;
        }
        return statWipeHandler.execute(username, "", punishmentId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (violationTracker != null) violationTracker.resetPlayer(playerId);
        if (autoReporter != null) autoReporter.clearCooldown(playerId);
    }
}
