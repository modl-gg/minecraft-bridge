package gg.modl.bridge.command;

import gg.modl.bridge.locale.BridgeLocaleManager;
import gg.modl.bridge.query.BridgeQueryServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ProxyCmdCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final BridgeLocaleManager localeManager;
    private final BridgeQueryServer queryServer;

    public ProxyCmdCommand(JavaPlugin plugin, BridgeLocaleManager localeManager, BridgeQueryServer queryServer) {
        this.plugin = plugin;
        this.localeManager = localeManager;
        this.queryServer = queryServer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.console_only"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.usage"));
            return true;
        }

        // Check if modl plugin is installed locally on Spigot (not supported — use proxy)
        if (Bukkit.getPluginManager().getPlugin("modl") != null) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.modl_on_spigot"));
            return true;
        }

        // Check if connected to modl plugin via TCP query
        if (!queryServer.hasConnectedClients()) {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.not_connected"));
            return true;
        }

        String fullCommand = String.join(" ", args);

        boolean sent = queryServer.sendToAllClients("PROXY_CMD", fullCommand);
        if (sent) {
            plugin.getLogger().info("[ModlBridge] Forwarded command to proxy: " + fullCommand);
            sender.sendMessage(localeManager.getMessage("command.proxycmd.sent", java.util.Map.of("command", fullCommand)));
        } else {
            sender.sendMessage(localeManager.getMessage("command.proxycmd.failed"));
        }

        return true;
    }
}
