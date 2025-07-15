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

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;

/* Builder class for creating an instance of {@link Options}. */
public class Options {

  static int DEFAULT_NUM_GRPC_CHANNELS = 4;

  public static class Builder {
    String host;
    int port;
    InetAddress inetAddress;
    String databaseUri;
    int numGrpcChannels = DEFAULT_NUM_GRPC_CHANNELS;
    Optional<Duration> maxCommitDelay = Optional.empty();

    public Builder() {}

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    /** The local TCP port number that the adapter server should listen on. */
    public Builder port(int port) {
      this.port = port;
      return this;
    }

    /** The specific local {@link InetAddress} for the server socket to bind to. */
    public Builder inetAddress(InetAddress inetAddress) {
      this.inetAddress = inetAddress;
      return this;
    }

    /** The URI of the Cloud Spanner database to connect to. */
    public Builder databaseUri(String databaseUri) {
      this.databaseUri = databaseUri;
      return this;
    }

    /** (Optional) The number of gRPC channels to use for connections to Cloud Spanner. */
    public Builder numGrpcChannels(int numGrpcChannels) {
      this.numGrpcChannels = numGrpcChannels;
      return this;
    }

    /** (Optional) The max commit delay to set in requests to optimize write throughput. */
    public Builder maxCommitDelay(Duration maxCommitDelay) {
      this.maxCommitDelay = Optional.ofNullable(maxCommitDelay);
      return this;
    }

    public Options build() {
      return new Options(this);
    }
  }

  private final String host;
  private final int port;
  private final InetAddress inetAddress;
  private final String databaseUri;
  private final int numGrpcChannels;
  private final Optional<Duration> maxCommitDelay;

  private Options(Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.inetAddress = builder.inetAddress;
    this.databaseUri = builder.databaseUri;
    this.numGrpcChannels = builder.numGrpcChannels;
    this.maxCommitDelay = builder.maxCommitDelay;
  }

  String getHost() {
    return host;
  }

  int getPort() {
    return port;
  }

  String getDatabaseUri() {
    return databaseUri;
  }

  InetAddress getInetAddress() {
    return inetAddress;
  }

  int getNumGrpcChannels() {
    return numGrpcChannels;
  }

  Optional<Duration> getMaxCommitDelay() {
    return maxCommitDelay;
  }
}
