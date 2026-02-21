package gg.modl.bridge.command;

import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.http.BridgeHttpClient;
import gg.modl.bridge.http.request.CreatePunishmentRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class AnticheatPunishCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final BridgeHttpClient httpClient;

    public AnticheatPunishCommand(JavaPlugin plugin, BridgeConfig config, BridgeHttpClient httpClient) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("[ModlBridge] This command can only be executed from console.");
            return true;
        }

        if (args.length < 1) {
            if (label.equalsIgnoreCase("anticheat-ban")) {
                sender.sendMessage("Usage: /anticheat-ban <player> [-lenient|-normal|-severe] [notes...]");
            } else {
                sender.sendMessage("Usage: /anticheat-kick <player> [kick message...]");
            }
            return true;
        }

        String playerName = args[0];
        boolean isBan = label.equalsIgnoreCase("anticheat-ban");

        // Parse severity flag and reason from remaining args
        String severity = "NORMAL";
        StringBuilder reasonBuilder = new StringBuilder();

        for (int i = 1; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "-lenient":
                    severity = "LOW";
                    break;
                case "-normal":
                    severity = "NORMAL";
                    break;
                case "-severe":
                    severity = "HIGH";
                    break;
                default:
                    if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                    reasonBuilder.append(args[i]);
                    break;
            }
        }

        String reason = reasonBuilder.toString();

        // Resolve player UUID
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        UUID targetUuid;
        if (onlinePlayer != null) {
            targetUuid = onlinePlayer.getUniqueId();
            playerName = onlinePlayer.getName();
        } else {
            @SuppressWarnings("deprecation")
            UUID offlineUuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            targetUuid = offlineUuid;
        }

        if (isBan) {
            executeBan(targetUuid, playerName, reason, severity);
        } else {
            executeKick(targetUuid, playerName, reason);
        }

        return true;
    }

    private void executeBan(UUID targetUuid, String playerName, String reason, String severity) {
        if (reason.isEmpty()) {
            reason = "Unfair Advantage";
        }

        List<String> notes = List.of("Issued via /anticheat-ban console command");

        CreatePunishmentRequest request = new CreatePunishmentRequest(
                targetUuid.toString(),
                config.getIssuerName(),
                config.getBanTypeOrdinal(),
                reason,
                null,
                null,
                notes,
                null,
                severity,
                "ACTIVE"
        );

        plugin.getLogger().info("[ModlBridge] Executing anticheat-ban for " + playerName + " (severity: " + severity + "): " + reason);

        httpClient.createPunishment(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Ban created for " + playerName + " - ID: " + response.getPunishmentId());
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to ban " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error banning " + playerName + ": " + throwable.getMessage());
            return null;
        });
    }

    private void executeKick(UUID targetUuid, String playerName, String reason) {
        if (reason.isEmpty()) {
            reason = "Kicked by anticheat";
        }

        List<String> notes = List.of("Issued via /anticheat-kick console command");

        CreatePunishmentRequest request = new CreatePunishmentRequest(
                targetUuid.toString(),
                config.getIssuerName(),
                config.getKickTypeOrdinal(),
                reason,
                0L,
                null,
                notes,
                null,
                "NORMAL",
                "ACTIVE"
        );

        plugin.getLogger().info("[ModlBridge] Executing anticheat-kick for " + playerName + ": " + reason);

        httpClient.createPunishment(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Kick punishment created for " + playerName + " - ID: " + response.getPunishmentId());
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to kick " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error kicking " + playerName + ": " + throwable.getMessage());
            return null;
        });
    }
}
