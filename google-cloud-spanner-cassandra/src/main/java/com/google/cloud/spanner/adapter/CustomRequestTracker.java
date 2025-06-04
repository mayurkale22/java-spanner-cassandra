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

import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.api.core.tracker.RequestTracker;
import com.datastax.oss.driver.internal.core.cql.DefaultBoundStatement;
import com.datastax.oss.driver.internal.core.cql.DefaultSimpleStatement;
import com.google.auth.oauth2.GoogleCredentials;
import io.opentelemetry.api.OpenTelemetry;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

enum CustomRequestTracker implements RequestTracker {
  INSTANCE;

  private static final Logger LOG = LoggerFactory.getLogger(CustomRequestTracker.class);

  private final BuiltInMetricsProvider builtInMetricsProvider = BuiltInMetricsProvider.INSTANCE;
  private final MetricsRecorder metricsRecorder;

  private CustomRequestTracker() {

    GoogleCredentials credentials = null;
    try {
      credentials = GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      e.printStackTrace();
    }

    OpenTelemetry openTelemetry =
        builtInMetricsProvider.getOrCreateOpenTelemetry("databaseuri", credentials);
    metricsRecorder =
        new MetricsRecorder(
            openTelemetry,
            BuiltInMetricsConstant.METER_NAME,
            builtInMetricsProvider.createClientAttributes());
  }

  @Override
  public void onSuccess(
      @NonNull Request request,
      long latencyNanos,
      @NonNull DriverExecutionProfile executionProfile,
      @NonNull Node node,
      @NonNull String requestLogPrefix) {

    String postfix = "";
    if (request instanceof DefaultBoundStatement) {
      postfix = "EXECUTE";
    } else if (request instanceof DefaultSimpleStatement) {
      postfix = "QUERY";
    }

    Map<String, String> attributes = new HashMap<>();
    attributes.put("database", "analytics2");
    attributes.put("method", "AdaptMessage." + postfix);
    attributes.put("status", "OK");
    double latencyMillis = (double) latencyNanos / 1_000_000.0;
    System.out.println(
        Instant.now() + " : " + request.getClass().getSimpleName() + " - " + latencyMillis + " ms");
    metricsRecorder.recordOperationCount(1, attributes);
    metricsRecorder.recordOperationLatency(latencyMillis, attributes);
  }

  @Override
  public void onError(
      @NonNull Request request,
      @NonNull Throwable error,
      long latencyNanos,
      @NonNull DriverExecutionProfile executionProfile,
      @NonNull Node node,
      @NonNull String requestLogPrefix) {
    System.out.println("** onError: " + request.toString());
    System.out.println("** Error: " + error.getMessage());
  }

  @Override
  public void onSessionReady(@NonNull Session session) {
    LOG.info("******** inside onSessionReady");
  }

  @Override
  public void close() throws Exception {}
}
