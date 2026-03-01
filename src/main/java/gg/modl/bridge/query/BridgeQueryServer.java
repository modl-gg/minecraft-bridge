package gg.modl.bridge.query;

import gg.modl.bridge.statwipe.StatWipeHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class BridgeQueryServer {
    private final int port;
    private final String secret;
    private final StatWipeHandler statWipeHandler;
    private final JavaPlugin plugin;
    private final Logger logger;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public BridgeQueryServer(int port, String secret, StatWipeHandler statWipeHandler, JavaPlugin plugin) {
        this.port = port;
        this.secret = secret;
        this.statWipeHandler = statWipeHandler;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
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
                                new BridgeQueryHandler(secret, statWipeHandler, plugin)
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
