package gg.modl.bridge.statwipe;

import gg.modl.bridge.config.BridgeConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

public class StatWipeHandler {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final Logger logger;

    public StatWipeHandler(JavaPlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    /**
     * Execute stat-wipe commands for the given player.
     * Must be called from the main server thread.
     *
     * @param username     the player's username
     * @param uuid         the player's UUID
     * @param punishmentId the punishment ID (for logging)
     * @return true if all commands executed successfully
     */
    public boolean execute(String username, String uuid, String punishmentId) {
        List<String> commands = config.getStatWipeCommands();
        if (commands.isEmpty()) {
            logger.warning("[StatWipe] No stat-wipe-commands configured in config.yml");
            return false;
        }

        boolean allSuccess = true;
        for (String template : commands) {
            String command = template
                    .replace("{player}", username)
                    .replace("{uuid}", uuid);
            try {
                if (config.isDebug()) {
                    logger.info("[StatWipe] Dispatching command: " + command);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception e) {
                logger.severe("[StatWipe] Failed to execute command '" + command + "': " + e.getMessage());
                allSuccess = false;
            }
        }

        if (allSuccess) {
            logger.info("[StatWipe] Executed " + commands.size() + " stat-wipe command(s) for " + username +
                    " (punishment: " + punishmentId + ")");
        }
        return allSuccess;
    }
}
