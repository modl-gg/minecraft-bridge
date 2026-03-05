package gg.modl.bridge.handler;

import gg.modl.bridge.locale.BridgeLocaleManager;
import gg.modl.bridge.query.BridgeQueryServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeHandler implements Listener {

    private final JavaPlugin plugin;
    private final BridgeLocaleManager localeManager;
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>(); // frozen -> staff
    private StaffModeHandler staffModeHandler;
    private BridgeQueryServer queryServer;

    public FreezeHandler(JavaPlugin plugin, BridgeLocaleManager localeManager) {
        this.plugin = plugin;
        this.localeManager = localeManager;
    }

    public void setStaffModeHandler(StaffModeHandler staffModeHandler) {
        this.staffModeHandler = staffModeHandler;
    }

    public void setQueryServer(BridgeQueryServer queryServer) {
        this.queryServer = queryServer;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void freeze(String targetUuid, String staffUuid) {
        UUID target = UUID.fromString(targetUuid);
        UUID staff = UUID.fromString(staffUuid);
        frozenPlayers.put(target, staff);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.sendMessage(localeManager.getMessage("freeze.frozen"));
            }
        });
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.sendMessage(localeManager.getMessage("freeze.unfrozen"));
            }
        });
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        // Allow head rotation, block XYZ movement
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player.getUniqueId())) return;
        event.setCancelled(true);
        // Redirect chat to staff in staff mode
        String message = localeManager.getMessage("freeze.chat", Map.of("player", player.getName(), "message", event.getMessage()));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (staffModeHandler != null && staffModeHandler.isInStaffMode(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }
        // Also send to the frozen player so they see their own message
        player.sendMessage(message);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(localeManager.getMessage("freeze.no_commands"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        if (frozenPlayers.remove(uuid) != null) {
            plugin.getLogger().warning("Frozen player " + playerName + " logged out!");

            // Notify proxy so staff across the network are alerted
            if (queryServer != null) {
                queryServer.sendToAllClients("FREEZE_LOGOUT", uuid.toString(), playerName);
            }
        }
    }
}
