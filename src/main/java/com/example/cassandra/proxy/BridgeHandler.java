package com.example.cassandra.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

class BridgeHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeHandler.class);

    private static final AttributeKey<Channel> PEER_KEY = AttributeKey.valueOf("peer");

    private final OutboundBootstrapFactory outboundFactory;
    private final String targetHost;
    private final int targetPort;
    private final int bodyPreviewBytes;
    private final CassandraProxyServer.Direction direction;

    private final AtomicBoolean connecting = new AtomicBoolean(false);

    @FunctionalInterface
    interface OutboundBootstrapFactory { Bootstrap create(); }

    BridgeHandler(OutboundBootstrapFactory outboundFactory,
                  String targetHost,
                  int targetPort,
                  int bodyPreviewBytes,
                  CassandraProxyServer.Direction direction) {
        this.outboundFactory = outboundFactory;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.bodyPreviewBytes = bodyPreviewBytes;
        this.direction = direction;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ensurePeerConnected(ctx);
        ctx.fireChannelActive();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        Channel peer = ctx.channel().attr(PEER_KEY).get();
        if (peer == null || !peer.isActive()) {
            ensurePeerConnected(ctx);
        }
        try {
            logFrame(ctx, frame);
            if (peer != null) {
                peer.writeAndFlush(frame.retain());
            } else {
                ReferenceCountUtil.release(frame);
            }
        } catch (Throwable t) {
            ReferenceCountUtil.release(frame);
            throw t;
        }
    }

    private void ensurePeerConnected(ChannelHandlerContext ctx) {
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        if (direction == CassandraProxyServer.Direction.CLIENT_TO_SERVER) {
            Bootstrap b = outboundFactory.create();
            Channel inbound = ctx.channel();
            b.connect(new InetSocketAddress(targetHost, targetPort))
                    .addListener((ChannelFutureListener) future -> {
                        connecting.set(false);
                        if (future.isSuccess()) {
                            Channel outbound = future.channel();
                            inbound.attr(PEER_KEY).set(outbound);
                            outbound.attr(PEER_KEY).set(inbound);
                            // Add reverse bridge on server channel to flow responses back
                            outbound.pipeline().addLast("serverBridge", new BridgeHandler(outboundFactory, targetHost, targetPort, bodyPreviewBytes, CassandraProxyServer.Direction.SERVER_TO_CLIENT));
                        } else {
                            LOGGER.error("Failed to connect to target {}:{}", targetHost, targetPort, future.cause());
                            inbound.close();
                        }
                    });
        }
    }

    private void logFrame(ChannelHandlerContext ctx, ByteBuf frame) {
        if (!LOGGER.isInfoEnabled()) {
            return;
        }
        int idx = frame.readerIndex();
        short version = (short) (frame.getUnsignedByte(idx) & 0xFF);
        short flags = (short) (frame.getUnsignedByte(idx + 1) & 0xFF);
        short streamId = frame.getShort(idx + 2);
        short opcode = (short) (frame.getUnsignedByte(idx + 4) & 0xFF);
        int bodyLength = frame.getInt(idx + 5);

        String opcodeName = CqlUtils.opcodeToString(opcode);
        String dir = direction == CassandraProxyServer.Direction.CLIENT_TO_SERVER ? "REQ" : "RES";

        StringBuilder sb = new StringBuilder();
        sb.append(dir)
                .append(" v=").append(String.format("0x%02X", version))
                .append(" flags=").append(String.format("0x%02X", flags))
                .append(" stream=").append(streamId)
                .append(" opcode=").append(opcodeName)
                .append(" bodyLength=").append(bodyLength);

        int headerLen = 9;
        int previewLen = Math.min(bodyPreviewBytes, Math.max(0, frame.readableBytes() - headerLen));
        if (previewLen > 0) {
            ByteBuf slice = frame.slice(idx + headerLen, previewLen);
            String hex = ByteBufUtil.hexDump(slice);
            sb.append(" body[0..").append(previewLen).append(")=").append(hex);
        }

        LOGGER.info(sb.toString());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel peer = ctx.channel().attr(PEER_KEY).getAndSet(null);
        if (peer != null && peer.isActive()) {
            peer.close();
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.warn("Exception on {}: {}", direction, cause.toString());
        ctx.close();
    }
}