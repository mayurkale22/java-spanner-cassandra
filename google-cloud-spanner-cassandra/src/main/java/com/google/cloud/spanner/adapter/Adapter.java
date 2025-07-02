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

import com.google.api.gax.core.GaxProperties;
import com.google.api.gax.grpc.ChannelPoolSettings;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.rpc.FixedHeaderProvider;
import com.google.api.gax.rpc.HeaderProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant;
import com.google.cloud.spanner.adapter.metrics.BuiltInMetricsProvider;
import com.google.cloud.spanner.adapter.metrics.BuiltInMetricsRecorder;
import com.google.spanner.adapter.v1.AdapterClient;
import com.google.spanner.adapter.v1.AdapterSettings;
import io.opentelemetry.api.OpenTelemetry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.concurrent.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages client connections, acting as an intermediary for communication with Spanner. */
@NotThreadSafe
final class Adapter {
  private static final Logger LOG = LoggerFactory.getLogger(Adapter.class);

  private final BuiltInMetricsProvider builtInMetricsProvider = BuiltInMetricsProvider.INSTANCE;
  private final BuiltInMetricsRecorder metricsRecorder;

  private static final String RESOURCE_PREFIX_HEADER_KEY = "google-cloud-resource-prefix";
  private static final long MAX_GLOBAL_STATE_SIZE = (long) (1e8 / 256); // ~100 MB
  private static final int DEFAULT_CONNECTION_BACKLOG = 50;
  private static final String ENV_VAR_GOOGLE_SPANNER_ENABLE_DIRECT_ACCESS =
      "GOOGLE_SPANNER_ENABLE_DIRECT_ACCESS";

  private static final String USER_AGENT_KEY = "user-agent";
  private static final String CLIENT_LIBRARY_LANGUAGE = "java-spanner-cassandra";
  private static final String CLIENT_VERSION = "0.3.0"; // {x-release-please-version}
  public static final String DEFAULT_USER_AGENT =
      CLIENT_LIBRARY_LANGUAGE
          + "/v"
          + CLIENT_VERSION
          + GaxProperties.getLibraryVersion(Adapter.class);

  private final InetAddress inetAddress;
  private final int port;
  private final String host;
  private final String databaseUri;
  private final int numGrpcChannels;
  private final Optional<Duration> maxCommitDelay;
  private AdapterClientWrapper adapterClientWrapper;
  private ServerSocket serverSocket;
  private ExecutorService executor;
  private boolean started = false;

  /**
   * Constructor for the Adapter class, specifying a specific address to bind to.
   *
   * @param databaseUri The URI of the Cloud Spanner database to connect to.
   * @param inetAddress The specific local {@link InetAddress} for the server socket to bind to.
   * @param port The local TCP port number that the adapter server should listen on.
   * @param numGrpcChannels The number of gRPC channels to use for communication with the backend
   *     Spanner service.
   * @param maxCommitDelay The max commit delay to set in requests to optimize write throughput.
   */
  Adapter(
      String host,
      String databaseUri,
      InetAddress inetAddress,
      int port,
      int numGrpcChannels,
      Optional<Duration> maxCommitDelay) {
    // TODO: Encapsulate arguments in an Options class to accomodate future fields without having to
    // pass them individually.
    this.host = host;
    this.databaseUri = databaseUri;
    this.inetAddress = inetAddress;
    this.port = port;
    this.numGrpcChannels = numGrpcChannels;
    this.maxCommitDelay = maxCommitDelay;

    GoogleCredentials credentials = null;
    try {
      credentials = GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      e.printStackTrace();
    }

    OpenTelemetry openTelemetry =
        builtInMetricsProvider.getOrCreateOpenTelemetry("span-cloud-testing", "c2sp", credentials);
    metricsRecorder =
        new BuiltInMetricsRecorder(
            openTelemetry,
            BuiltInMetricsConstant.METER_NAME,
            builtInMetricsProvider.createClientAttributes());
  }

  /** Starts the adapter, initializing the local TCP server and handling client connections. */
  void start() {
    if (started) {
      return;
    }

    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

      InstantiatingGrpcChannelProvider.Builder channelProviderBuilder =
          AdapterSettings.defaultGrpcTransportProviderBuilder();

      channelProviderBuilder
          .setCredentials(credentials)
          .setChannelPoolSettings(ChannelPoolSettings.staticallySized(numGrpcChannels));

      if (isEnableDirectPathXdsEnv()) {
        channelProviderBuilder.setAttemptDirectPath(true);
        channelProviderBuilder.setAttemptDirectPathXds();
      }
      HeaderProvider headerProvider =
          FixedHeaderProvider.create(
              RESOURCE_PREFIX_HEADER_KEY, databaseUri, USER_AGENT_KEY, DEFAULT_USER_AGENT);
      AdapterSettings settings =
          AdapterSettings.newBuilder()
              .setEndpoint(host)
              .setTransportChannelProvider(channelProviderBuilder.build())
              .setHeaderProvider(headerProvider)
              .build();

      AdapterClient adapterClient = AdapterClient.create(settings);

      AttachmentsCache attachmentsCache = new AttachmentsCache(MAX_GLOBAL_STATE_SIZE);
      SessionManager sessionManager = new SessionManager(adapterClient, databaseUri);

      // Create initial session to verify database existence
      sessionManager.getSession();

      adapterClientWrapper =
          new AdapterClientWrapper(adapterClient, attachmentsCache, sessionManager);

      // Start listening on the specified host and port.
      serverSocket = new ServerSocket(port, DEFAULT_CONNECTION_BACKLOG, inetAddress);
      LOG.info("Local TCP server started on {}:{}", inetAddress, port);

      executor = Executors.newCachedThreadPool();

      // Start accepting client connections.
      executor.execute(this::acceptClientConnections);

      started = true;
      LOG.info("Adapter started for database '{}'.", databaseUri);

    } catch (IOException | RuntimeException e) {
      throw new AdapterStartException(e);
    }
  }

  /**
   * Stops the adapter, shutting down the executor, closing the server socket, and closing the
   * adapter client.
   *
   * @throws IOException If an I/O error occurs while closing the server socket.
   */
  void stop() throws IOException {
    if (!started) {
      throw new IllegalStateException("Adapter was never started!");
    }
    executor.shutdownNow();
    serverSocket.close();
    LOG.info("Adapter stopped.");
  }

  private void acceptClientConnections() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        final Socket clientSocket = serverSocket.accept();
        executor.execute(
            new DriverConnectionHandler(
                clientSocket, adapterClientWrapper, maxCommitDelay, metricsRecorder));
        LOG.info("Accepted client connection from: {}", clientSocket.getRemoteSocketAddress());
      }
    } catch (SocketException e) {
      if (!serverSocket.isClosed()) {
        LOG.error("Error accepting client connection", e);
      }
    } catch (IOException e) {
      LOG.error("Error accepting client connection", e);
    }
  }

  private static boolean isEnableDirectPathXdsEnv() {
    return Boolean.parseBoolean(System.getenv(ENV_VAR_GOOGLE_SPANNER_ENABLE_DIRECT_ACCESS));
  }

  private static final class AdapterStartException extends RuntimeException {
    public AdapterStartException(Throwable cause) {
      super("Failed to start the adapter.", cause);
    }
  }
}
