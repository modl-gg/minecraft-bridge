package gg.modl.bridge.query;

import gg.modl.bridge.statwipe.StatWipeHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

public class BridgeQueryHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] MAGIC = "modl".getBytes(StandardCharsets.US_ASCII);

    private final String secret;
    private final StatWipeHandler statWipeHandler;
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean authenticated = false;

    public BridgeQueryHandler(String secret, StatWipeHandler statWipeHandler, JavaPlugin plugin) {
        this.secret = secret;
        this.statWipeHandler = statWipeHandler;
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

            if ("STAT_WIPE".equals(action)) {
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
            } else {
                logger.info("[ModlBridge] Received unknown query action: " + action);
            }
        } catch (IOException e) {
            logger.warning("[ModlBridge] Failed to read query message: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warning("[ModlBridge] Query connection error: " + cause.getMessage());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (authenticated) {
            logger.info("[ModlBridge] Query client disconnected: " + ctx.channel().remoteAddress());
        }
    }
}
