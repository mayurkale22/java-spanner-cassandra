package com.example.cassandra.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CassandraProxyServer starts a TCP proxy that intercepts Cassandra native protocol
 * traffic (default port 9042), logs frame headers and a snippet of the body,
 * and forwards bytes transparently to the configured Cassandra node.
 */
public class CassandraProxyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraProxyServer.class);

    private final String listenHost;
    private final int listenPort;
    private final String targetHost;
    private final int targetPort;
    private final int bodyPreviewBytes;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private CassandraProxyServer(Builder builder) {
        this.listenHost = builder.listenHost;
        this.listenPort = builder.listenPort;
        this.targetHost = builder.targetHost;
        this.targetPort = builder.targetPort;
        this.bodyPreviewBytes = builder.bodyPreviewBytes;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public CompletableFuture<Void> startAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!started.compareAndSet(false, true)) {
            future.complete(null);
            return future;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("cqlFrameDecoder", new CqlFrameDecoder());
                            pipeline.addLast("clientBridge", new BridgeHandler(() -> createOutboundBootstrap(ch.eventLoop()), targetHost, targetPort, bodyPreviewBytes, Direction.CLIENT_TO_SERVER));
                        }
                    });

            ChannelFuture bindFuture = serverBootstrap.bind(new InetSocketAddress(listenHost, listenPort));
            bindFuture.addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    serverChannel = f.channel();
                    LOGGER.info("Cassandra proxy listening on {}:{} forwarding to {}:{}", listenHost, listenPort, targetHost, targetPort);
                    future.complete(null);
                } else {
                    Throwable cause = f.cause();
                    LOGGER.error("Failed to bind {}:{}", listenHost, listenPort, cause);
                    future.completeExceptionally(cause);
                }
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }

        return future;
    }

    private Bootstrap createOutboundBootstrap(EventLoop eventLoop) {
        Bootstrap b = new Bootstrap();
        b.group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("cqlFrameDecoder", new CqlFrameDecoder());
                        // Inbound from server to client handled by BridgeHandler added from peer side
                    }
                });
        return b;
    }

    public CompletableFuture<Void> stopAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!started.compareAndSet(true, false)) {
            future.complete(null);
            return future;
        }
        Channel sc = this.serverChannel;
        if (sc != null) {
            sc.close();
        }
        CompletableFuture<Void> bossShutdown = new CompletableFuture<>();
        CompletableFuture<Void> workerShutdown = new CompletableFuture<>();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().addListener(f -> bossShutdown.complete(null));
        } else {
            bossShutdown.complete(null);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().addListener(f -> workerShutdown.complete(null));
        } else {
            workerShutdown.complete(null);
        }
        CompletableFuture.allOf(bossShutdown, workerShutdown).whenComplete((v, t) -> {
            if (t != null) {
                future.completeExceptionally(t);
            } else {
                LOGGER.info("Cassandra proxy stopped");
                future.complete(null);
            }
        });
        return future;
    }

    public void startBlocking() {
        startAsync().join();
    }

    public void stopBlocking() {
        stopAsync().join();
    }

    public static final class Builder {
        private String listenHost = "0.0.0.0";
        private int listenPort = 9042;
        private String targetHost;
        private int targetPort = 9042;
        private int bodyPreviewBytes = 128;

        public Builder listenHost(String listenHost) {
            this.listenHost = Objects.requireNonNull(listenHost, "listenHost");
            return this;
        }

        public Builder listenPort(int listenPort) {
            this.listenPort = listenPort;
            return this;
        }

        public Builder targetHost(String targetHost) {
            this.targetHost = Objects.requireNonNull(targetHost, "targetHost");
            return this;
        }

        public Builder targetPort(int targetPort) {
            this.targetPort = targetPort;
            return this;
        }

        public Builder bodyPreviewBytes(int bodyPreviewBytes) {
            this.bodyPreviewBytes = Math.max(0, bodyPreviewBytes);
            return this;
        }

        public CassandraProxyServer build() {
            if (targetHost == null || targetHost.isBlank()) {
                throw new IllegalStateException("targetHost must be set");
            }
            return new CassandraProxyServer(this);
        }
    }

    enum Direction { CLIENT_TO_SERVER, SERVER_TO_CLIENT }
}