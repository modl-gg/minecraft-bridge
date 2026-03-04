package gg.modl.bridge.query;

import gg.modl.bridge.handler.FreezeHandler;
import gg.modl.bridge.handler.StaffModeHandler;
import gg.modl.bridge.statwipe.StatWipeHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

public class BridgeQueryHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);

    private final String secret;
    private final StatWipeHandler statWipeHandler;
    private final FreezeHandler freezeHandler;
    private final StaffModeHandler staffModeHandler;
    private final BridgeQueryServer queryServer;
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean authenticated = false;

    public BridgeQueryHandler(String secret, StatWipeHandler statWipeHandler, FreezeHandler freezeHandler, StaffModeHandler staffModeHandler, BridgeQueryServer queryServer, JavaPlugin plugin) {
        this.secret = secret;
        this.statWipeHandler = statWipeHandler;
        this.freezeHandler = freezeHandler;
        this.staffModeHandler = staffModeHandler;
        this.queryServer = queryServer;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            if (!authenticated) {
                handleHandshake(ctx, buf);
            } else {
                handleMessage(ctx, buf);
            }
        } finally {
            buf.release();
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) {
        // Expect: [4 bytes: "modl" magic] [UTF: secret]
        if (buf.readableBytes() < 6) {
            logger.warning("[ModlBridge] Query handshake too short from " + ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        byte[] magic = new byte[4];
        buf.readBytes(magic);

        if (!Arrays.equals(magic, MAGIC)) {
            logger.warning("[ModlBridge] Query handshake failed: invalid magic bytes from " + ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        byte[] remaining = new byte[buf.readableBytes()];
        buf.readBytes(remaining);

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(remaining));
            String clientSecret = in.readUTF();

            if (!secret.equals(clientSecret)) {
                logger.warning("[ModlBridge] Query handshake failed: invalid secret from " + ctx.channel().remoteAddress());
                sendResponse(ctx, (byte) 0x00);
                ctx.close();
                return;
            }

            authenticated = true;
            queryServer.addAuthenticatedChannel(ctx.channel());
            sendResponse(ctx, (byte) 0x01);
            logger.info("[ModlBridge] Query client authenticated from " + ctx.channel().remoteAddress());

            sendBridgeHello(ctx);
        } catch (IOException e) {
            logger.warning("[ModlBridge] Query handshake error: " + e.getMessage());
            ctx.close();
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, byte status) {
        ByteBuf response = ctx.alloc().buffer(1);
        response.writeByte(status);
        ctx.writeAndFlush(response);
    }

    private void sendBridgeHello(ChannelHandlerContext ctx) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("BRIDGE_HELLO");
            out.writeUTF(Bukkit.getServer().getName());

            ByteBuf buf = ctx.alloc().buffer();
            buf.writeBytes(bytes.toByteArray());
            ctx.writeAndFlush(buf);
            logger.info("[ModlBridge] Sent BRIDGE_HELLO via TCP query");
        } catch (IOException e) {
            logger.warning("[ModlBridge] Failed to send BRIDGE_HELLO: " + e.getMessage());
        }
    }

    private void handleMessage(ChannelHandlerContext ctx, ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            String action = in.readUTF();

            switch (action) {
                case "STAT_WIPE" -> {
                    String username = in.readUTF();
                    String uuid = in.readUTF();
                    String punishmentId = in.readUTF();

                    logger.info("[StatWipe] Processing stat wipe for " + username +
                            " (punishment: " + punishmentId + ") via TCP query");

                    // Execute on main thread (dispatchCommand requires main thread)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        boolean success = statWipeHandler.execute(username, uuid, punishmentId);
                        logger.info("[StatWipe] Stat wipe for " + username + " " +
                                (success ? "succeeded" : "failed") +
                                " (punishment: " + punishmentId + ")");
                    });
                }
                case "FREEZE_PLAYER" -> {
                    String targetUuid = in.readUTF();
                    String staffUuid = in.readUTF();
                    // Broadcast to all connected servers (including origin for enforcement)
                    broadcastCurrentMessage(ctx, "FREEZE_PLAYER", targetUuid, staffUuid);
                    // Execute locally on this Spigot server
                    freezeHandler.freeze(targetUuid, staffUuid);
                }
                case "UNFREEZE_PLAYER" -> {
                    String targetUuid = in.readUTF();
                    broadcastCurrentMessage(ctx, "UNFREEZE_PLAYER", targetUuid);
                    freezeHandler.unfreeze(targetUuid);
                }
                case "FREEZE_LOGOUT" -> {
                    String playerUuid = in.readUTF();
                    String playerName = in.readUTF();
                    // Broadcast freeze logout notification to all other servers
                    broadcastCurrentMessage(ctx, "FREEZE_LOGOUT", playerUuid, playerName);
                }
                case "STAFF_MODE_ENTER" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastCurrentMessage(ctx, "STAFF_MODE_ENTER", staffUuid, staffName);
                    staffModeHandler.enterStaffMode(staffUuid);
                }
                case "STAFF_MODE_EXIT" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastCurrentMessage(ctx, "STAFF_MODE_EXIT", staffUuid, staffName);
                    staffModeHandler.exitStaffMode(staffUuid);
                }
                case "VANISH_ENTER" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastCurrentMessage(ctx, "VANISH_ENTER", staffUuid, staffName);
                    staffModeHandler.vanishFromBridge(staffUuid);
                }
                case "VANISH_EXIT" -> {
                    String staffUuid = in.readUTF();
                    String staffName = in.readUTF();
                    broadcastCurrentMessage(ctx, "VANISH_EXIT", staffUuid, staffName);
                    staffModeHandler.unvanishFromBridge(staffUuid);
                }
                case "TARGET_REQUEST" -> {
                    String staffUuid = in.readUTF();
                    String targetUuid = in.readUTF();
                    // Check if target is on this server, respond with location
                    handleTargetRequest(ctx, staffUuid, targetUuid);
                }
                case "CONNECT_SERVER" -> {
                    // This is a proxy-only message, bridge just forwards
                    String playerUuid = in.readUTF();
                    String serverName = in.readUTF();
                    broadcastCurrentMessage(ctx, "CONNECT_SERVER", playerUuid, serverName);
                }
                default -> logger.info("[ModlBridge] Received unknown query action: " + action);
            }
        } catch (IOException e) {
            logger.warning("[ModlBridge] Failed to read query message: " + e.getMessage());
        }
    }

    private void broadcastCurrentMessage(ChannelHandlerContext ctx, String action, String... args) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(action);
            for (String arg : args) {
                dos.writeUTF(arg);
            }
            dos.flush();
            queryServer.broadcastMessage(baos.toByteArray(), ctx.channel());
        } catch (Exception e) {
            logger.warning("[ModlBridge] Failed to broadcast " + action + ": " + e.getMessage());
        }
    }

    private void handleTargetRequest(ChannelHandlerContext ctx, String staffUuid, String targetUuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player target = Bukkit.getPlayer(UUID.fromString(targetUuid));
            if (target != null && target.isOnline()) {
                // Target is on this server, send response back
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    dos.writeUTF("TARGET_RESPONSE");
                    dos.writeUTF(staffUuid);
                    dos.writeUTF(targetUuid);
                    dos.writeUTF(Bukkit.getServer().getName());
                    dos.flush();

                    byte[] responseData = baos.toByteArray();
                    ByteBuf buf = ctx.alloc().buffer(4 + responseData.length);
                    buf.writeInt(responseData.length);
                    buf.writeBytes(responseData);
                    ctx.writeAndFlush(buf);
                } catch (Exception e) {
                    logger.warning("[ModlBridge] Failed to send TARGET_RESPONSE: " + e.getMessage());
                }

                // Set up local staff mode targeting
                staffModeHandler.setTarget(staffUuid, targetUuid);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warning("[ModlBridge] Query connection error: " + cause.getMessage());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (authenticated) {
            queryServer.unregisterServer(ctx.channel());
            logger.info("[ModlBridge] Query client disconnected: " + ctx.channel().remoteAddress());
        }
    }
}
