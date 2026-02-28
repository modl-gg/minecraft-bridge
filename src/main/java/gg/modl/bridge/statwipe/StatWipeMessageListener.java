package gg.modl.bridge.statwipe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.logging.Logger;

public class StatWipeMessageListener implements PluginMessageListener {
    public static final String CHANNEL = "modl:statwipe";
    private final JavaPlugin plugin;
    private final StatWipeHandler handler;
    private final Logger logger;

    public StatWipeMessageListener(JavaPlugin plugin, StatWipeHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
        this.logger = plugin.getLogger();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String action = in.readUTF();

            if ("STAT_WIPE".equals(action)) {
                String username = in.readUTF();
                String uuid = in.readUTF();
                String punishmentId = in.readUTF();

                // Execute on main thread (dispatchCommand requires main thread)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean success = handler.execute(username, uuid, punishmentId);
                    sendAcknowledgement(player, punishmentId, success);
                });
            }
        } catch (IOException e) {
            logger.warning("[StatWipe] Failed to read plugin message: " + e.getMessage());
        }
    }

    private void sendAcknowledgement(Player player, String punishmentId, boolean success) {
        if (!player.isOnline()) return;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("STAT_WIPE_ACK");
            out.writeUTF(punishmentId);
            out.writeBoolean(success);
            out.writeUTF(Bukkit.getServer().getName());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            logger.warning("[StatWipe] Failed to send acknowledgement: " + e.getMessage());
        }
    }
}
