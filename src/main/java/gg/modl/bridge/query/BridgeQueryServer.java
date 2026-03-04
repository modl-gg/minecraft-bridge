package gg.modl.bridge.query;

import gg.modl.bridge.handler.FreezeHandler;
import gg.modl.bridge.handler.StaffModeHandler;
import gg.modl.bridge.statwipe.StatWipeHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class BridgeQueryServer {
    private final int port;
    private final String secret;
    private final StatWipeHandler statWipeHandler;
    private final FreezeHandler freezeHandler;
    private final StaffModeHandler staffModeHandler;
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<String, Channel> connectedServers = new ConcurrentHashMap<>();
    private final Set<Channel> authenticatedChannels = ConcurrentHashMap.newKeySet();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public BridgeQueryServer(int port, String secret, StatWipeHandler statWipeHandler, FreezeHandler freezeHandler, StaffModeHandler staffModeHandler, JavaPlugin plugin) {
        this.port = port;
        this.secret = secret;
        this.statWipeHandler = statWipeHandler;
        this.freezeHandler = freezeHandler;
        this.staffModeHandler = staffModeHandler;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void registerServer(String serverName, Channel channel) {
        connectedServers.put(serverName, channel);
    }

    public void addAuthenticatedChannel(Channel channel) {
        authenticatedChannels.add(channel);
    }

    public void unregisterServer(Channel channel) {
        connectedServers.entrySet().removeIf(entry -> entry.getValue().equals(channel));
        authenticatedChannels.remove(channel);
    }

    public void relayMessage(String targetServer, byte[] data) {
        Channel channel = connectedServers.get(targetServer);
        if (channel != null && channel.isActive()) {
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(data);
            channel.writeAndFlush(buf);
        }
    }

    public void broadcastMessage(byte[] data, Channel except) {
        for (Map.Entry<String, Channel> entry : connectedServers.entrySet()) {
            Channel ch = entry.getValue();
            if (ch.isActive() && !ch.equals(except)) {
                ByteBuf buf = ch.alloc().buffer();
                buf.writeBytes(data);
                ch.writeAndFlush(buf);
            }
        }
    }

    /**
     * Send a typed message to all connected proxy clients.
     * @return true if sent to at least one client
     */
    public boolean sendToAllClients(String action, String... args) {
        if (authenticatedChannels.isEmpty()) return false;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(action);
            for (String arg : args) {
                dos.writeUTF(arg);
            }
            dos.flush();
            byte[] data = baos.toByteArray();

            boolean sent = false;
            for (Channel ch : authenticatedChannels) {
                if (ch.isActive()) {
                    ByteBuf buf = ch.alloc().buffer();
                    buf.writeBytes(data);
                    ch.writeAndFlush(buf);
                    sent = true;
                }
            }
            return sent;
        } catch (IOException e) {
            logger.warning("[ModlBridge] Failed to send " + action + " to clients: " + e.getMessage());
            return false;
        }
    }

    public boolean hasConnectedClients() {
        return authenticatedChannels.stream().anyMatch(Channel::isActive);
    }

    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4),
                                new LengthFieldPrepender(4),
                                new BridgeQueryHandler(secret, statWipeHandler, freezeHandler, staffModeHandler, BridgeQueryServer.this, plugin)
                        );
                    }
                });

        try {
            serverChannel = bootstrap.bind(port).sync().channel();
            logger.info("[ModlBridge] Query server started on port " + port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.severe("[ModlBridge] Interrupted while starting query server on port " + port);
        } catch (Exception e) {
            logger.severe("[ModlBridge] Failed to start query server on port " + port + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
