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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for launching the Spanner Cassandra Adapter as a sidecar service.
 *
 * <p>This class reads configuration parameters from system properties, initializes the underlying
 * {@link Adapter}, registers a shutdown hook for graceful termination, and starts the adapter
 * service. The main thread then blocks indefinitely until the application is terminated.
 *
 * <p>Configuration is provided via the following system properties:
 *
 * <ul>
 *   <li>{@code databaseUri}: (Required) The URI of the target Spanner database.
 *   <li>{@code host}: (Optional) The hostname or IP address to bind the service to. Defaults to
 *       "0.0.0.0".
 *   <li>{@code port}: (Optional) The port number to bind the service to. Defaults to 9042.
 *   <li>{@code numGrpcChannels}: (Optional) The number of gRPC channels to use for communication
 *       with Spanner. Defaults to 4.
 * </ul>
 *
 * Example usage:
 *
 * <pre>
 * java -DdatabaseUri=projects/my-project/instances/my-instance/databases/my-database \
 * -Dhost=127.0.0.1 \
 * -Dport=9042 \
 * -DnumGrpcChannels=4 \
 * -cp path/to/your/spanner-cassandra-launcher.jar com.google.cloud.spanner.adapter.SpannerCassandraLauncher
 * </pre>
 *
 * @see Adapter
 */
public class SpannerCassandraLauncher {
  private static final Logger LOG = LoggerFactory.getLogger(SpannerCassandraLauncher.class);
  private static final String DATABASE_URI_PROP_KEY = "databaseUri";
  private static final String HOST_PROP_KEY = "host";
  private static final String PORT_PROP_KEY = "port";
  private static final String NUM_GRPC_CHANNELS_PROP_KEY = "numGrpcChannels";
  private static final String BUILTIN_METRICS_PROP_KEY = "builtInMetrics";
  private static final String DEFAULT_HOST = "0.0.0.0";
  private static final String DEFAULT_PORT = "9042";
  private static final String DEFAULT_NUM_GRPC_CHANNELS = "4";

  public static void main(String[] args) throws Exception {
    final String databaseUri = System.getProperty(DATABASE_URI_PROP_KEY);
    final InetAddress inetAddress =
        InetAddress.getByName(System.getProperty(HOST_PROP_KEY, DEFAULT_HOST));
    final int port = Integer.parseInt(System.getProperty(PORT_PROP_KEY, DEFAULT_PORT));
    final int numGrpcChannels =
        Integer.parseInt(System.getProperty(NUM_GRPC_CHANNELS_PROP_KEY, DEFAULT_NUM_GRPC_CHANNELS));
    final boolean enableBuiltInMetrics =
        Boolean.parseBoolean(System.getProperty(BUILTIN_METRICS_PROP_KEY));

    if (databaseUri == null) {
      throw new IllegalArgumentException(
          "Spanner database URI not set. Please set it using -DdatabaseUri option.");
    }

    Adapter adapter = new Adapter(databaseUri, inetAddress, port, numGrpcChannels, true);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    adapter.stop();
                  } catch (Exception e) {
                    LOG.error("Error stopping adapter during shutdown: " + e.getMessage(), e);
                  }
                }));

    LOG.info(
        "Starting Adapter for Spanner database {} on {}:{} with {} gRPC channels...",
        databaseUri,
        inetAddress,
        port,
        numGrpcChannels);

    adapter.start();

    try {
      // Wait until interrupted or terminated.
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }
}
