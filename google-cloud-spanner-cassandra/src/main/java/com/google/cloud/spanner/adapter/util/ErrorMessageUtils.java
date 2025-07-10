/*
Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.cloud.spanner.adapter.util;

import com.datastax.oss.driver.internal.core.protocol.ByteBufPrimitiveCodec;
import com.datastax.oss.protocol.internal.Compressor;
import com.datastax.oss.protocol.internal.Frame;
import com.datastax.oss.protocol.internal.FrameCodec;
import com.datastax.oss.protocol.internal.ProtocolConstants.ErrorCode;
import com.datastax.oss.protocol.internal.response.Error;
import com.datastax.oss.protocol.internal.response.Supported;
import com.datastax.oss.protocol.internal.response.error.Unprepared;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for creating specific types of error response frames used in the server protocol,
 * encoded as byte arrays suitable for network transmission.
 *
 * <p>This class provides static methods to generate common error responses like {@link
 * ErrorCode#SERVER_ERROR} and {@link ErrorCode#UNPREPARED}. It handles the necessary framing and
 * encoding using the defined protocol version and server codec.
 *
 * <p>This class cannot be instantiated.
 */
public final class ErrorMessageUtils {

  private static final int PROTOCOL_VERSION = 4;
  private static final FrameCodec<ByteBuf> serverFrameCodec =
      FrameCodec.defaultServer(
          new ByteBufPrimitiveCodec(ByteBufAllocator.DEFAULT), Compressor.none());

  private ErrorMessageUtils() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /**
   * Creates an unprepared error message response.
   *
   * @param queryId The query ID associated with the error.
   * @return A byte array representing the unprepared error response.
   */
  public static byte[] unpreparedResponse(byte[] queryId) {
    Unprepared errorMsg = new Unprepared("Unprepared", queryId);
    return errorResponse(errorMsg);
  }

  public static byte[] supportedResponse(int streamId) {
    Map<String, List<String>> options = new HashMap<>();
    options.put("CQL_VERSION", Collections.singletonList("3.0.0"));
    options.put("COMPRESSION", Collections.emptyList());
    Supported supported = new Supported(options);

    Frame responseFrame =
        Frame.forResponse(
            PROTOCOL_VERSION, streamId, null, Frame.NO_PAYLOAD, Collections.emptyList(), supported);
    ByteBuf responseBuf = serverFrameCodec.encode(responseFrame);
    return convertByteBufToByteArray(responseBuf);
  }

  /**
   * Creates a server error message response.
   *
   * @param message The error message.
   * @return A byte array representing the server error response.
   */
  public static byte[] serverErrorResponse(String message) {
    Error errorMsg = new Error(ErrorCode.SERVER_ERROR, message);
    return errorResponse(errorMsg);
  }

  /**
   * Creates an error response frame and converts it to a byte array.
   *
   * @param errorMsg The Error object containing the error details.
   * @return A byte array representing the error response.
   */
  public static byte[] errorResponse(Error errorMsg) {
    Frame responseFrame =
        Frame.forResponse(
            PROTOCOL_VERSION, -1, null, Frame.NO_PAYLOAD, Collections.emptyList(), errorMsg);
    ByteBuf responseBuf = serverFrameCodec.encode(responseFrame);
    return convertByteBufToByteArray(responseBuf);
  }

  private static byte[] convertByteBufToByteArray(ByteBuf srcBuf) {
    byte[] response = new byte[srcBuf.readableBytes()];
    srcBuf.readBytes(response);
    srcBuf.release();
    return response;
  }
}
