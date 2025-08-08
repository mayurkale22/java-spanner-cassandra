package com.example.cassandra.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Decodes Cassandra native protocol frames into whole ByteBuf frames.
 * Frame header (v3/v4):
 *  - 1 byte: version (request: 0x04, response: 0x84)
 *  - 1 byte: flags
 *  - 2 bytes: stream id (short)
 *  - 1 byte: opcode
 *  - 4 bytes: body length (int)
 *  - N bytes body
 */
public class CqlFrameDecoder extends ByteToMessageDecoder {

    private static final int HEADER_LENGTH = 9;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }
        in.markReaderIndex();
        int startIdx = in.readerIndex();

        short versionUnsigned = (short) (in.readUnsignedByte() & 0xFF);
        short flags = (short) (in.readUnsignedByte() & 0xFF);
        short streamId = in.readShort();
        short opcodeUnsigned = (short) (in.readUnsignedByte() & 0xFF);
        int bodyLength = in.readInt();

        if (bodyLength < 0) {
            // Invalid; close
            ctx.close();
            return;
        }
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }

        int frameLength = HEADER_LENGTH + bodyLength;
        in.readerIndex(startIdx);
        ByteBuf frame = in.readRetainedSlice(frameLength);
        out.add(frame);
    }
}