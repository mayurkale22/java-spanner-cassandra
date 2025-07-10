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

package com.google.cloud.spanner.adapter;

import com.datastax.oss.protocol.internal.ProtocolConstants;
import com.google.api.gax.rpc.ApiCallContext;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An object used to encapsulate the result of preparing the Adapter payload prior to sending the
 * gRPC request.
 */
public class PreparePayloadResult {
  private int opcode;
  private int streamId;
  private ApiCallContext context;
  private Map<String, String> attachments;
  private Optional<byte[]> attachmentErrorResponse;
  private static final Map<String, String> EMPTY_ATTACHMENTS = Collections.emptyMap();

  public PreparePayloadResult(
      int opcode,
      int streamId,
      ApiCallContext context,
      Map<String, String> attachments,
      Optional<byte[]> attachmentErrorResponse) {
    this.opcode = opcode;
    this.streamId = streamId;
    this.context = context;
    this.attachments = attachments;
    this.attachmentErrorResponse = attachmentErrorResponse;
  }

  public PreparePayloadResult(
      int opcode, int streamId, ApiCallContext context, Map<String, String> attachments) {
    this(opcode, streamId, context, attachments, Optional.empty());
  }

  public PreparePayloadResult(int opcode, int streamId, ApiCallContext context) {
    this(opcode, streamId, context, EMPTY_ATTACHMENTS, Optional.empty());
  }

  public Map<String, String> getAttachments() {
    return attachments;
  }

  public Optional<byte[]> getAttachmentErrorResponse() {
    return attachmentErrorResponse;
  }

  public ApiCallContext getContext() {
    return context;
  }

  public int getStreamId() {
    return streamId;
  }

  public String opcodeString() {
    switch (opcode) {
      case ProtocolConstants.Opcode.EXECUTE:
        return "EXECUTE";
      case ProtocolConstants.Opcode.PREPARE:
        return "PREPARE";
      case ProtocolConstants.Opcode.QUERY:
        return "QUERY";
      case ProtocolConstants.Opcode.BATCH:
        return "BATCH";
      case ProtocolConstants.Opcode.OPTIONS:
        return "OPTIONS";
      case ProtocolConstants.Opcode.ERROR:
        return "ERROR";
      case ProtocolConstants.Opcode.STARTUP:
        return "STARTUP";
      case ProtocolConstants.Opcode.READY:
        return "READY";
      case ProtocolConstants.Opcode.AUTHENTICATE:
        return "AUTHENTICATE";
      case ProtocolConstants.Opcode.SUPPORTED:
        return "SUPPORTED";
      case ProtocolConstants.Opcode.RESULT:
        return "RESULT";
      case ProtocolConstants.Opcode.REGISTER:
        return "REGISTER";
      case ProtocolConstants.Opcode.EVENT:
        return "EVENT";
      case ProtocolConstants.Opcode.AUTH_CHALLENGE:
        return "AUTH_CHALLENGE";
      case ProtocolConstants.Opcode.AUTH_RESPONSE:
        return "AUTH_RESPONSE";
      case ProtocolConstants.Opcode.AUTH_SUCCESS:
        return "AUTH_SUCCESS";
      default:
        return "0x" + Integer.toHexString(opcode);
    }
  }
}
