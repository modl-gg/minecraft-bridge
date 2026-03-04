package gg.modl.bridge.handler;

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
    private final Map<UUID, UUID> frozenPlayers = new ConcurrentHashMap<>(); // frozen -> staff

    public FreezeHandler(JavaPlugin plugin) {
        this.plugin = plugin;
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
                player.sendMessage("\u00a7c\u00a7lFREEZE \u00a78\u00bb \u00a77You have been frozen by a staff member. Do not disconnect.");
            }
        });
    }

    public void unfreeze(String targetUuid) {
        UUID target = UUID.fromString(targetUuid);
        frozenPlayers.remove(target);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.sendMessage("\u00a7a\u00a7lFREEZE \u00a78\u00bb \u00a77You have been unfrozen.");
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
        // Redirect chat to staff
        String message = "\u00a7b\u00a7lFROZEN \u00a78\u00bb \u00a7e" + player.getName() + "\u00a77: \u00a7f" + event.getMessage();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("modl.staff")) {
                online.sendMessage(message);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("\u00a7cYou cannot use commands while frozen.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (frozenPlayers.remove(uuid) != null) {
            // Player logged out while frozen - this will be detected by the core plugin
            // and broadcast via FREEZE_LOGOUT
            plugin.getLogger().warning("Frozen player " + event.getPlayer().getName() + " logged out!");
        }
    }
}
