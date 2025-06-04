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
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

enum CustomRequestTracker implements RequestTracker {
  INSTANCE;

  private static final Logger LOG = LoggerFactory.getLogger(CustomRequestTracker.class);

  @Override
  public void onSuccess(
      @NonNull Request request,
      long latencyNanos,
      @NonNull DriverExecutionProfile executionProfile,
      @NonNull Node node,
      @NonNull String requestLogPrefix) {

    double elapsedTimeMs = (double) latencyNanos / 1_000_000.0;
    System.out.println(
        Instant.now() + " : " + request.getClass().getSimpleName() + " - " + elapsedTimeMs + " ms");
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
