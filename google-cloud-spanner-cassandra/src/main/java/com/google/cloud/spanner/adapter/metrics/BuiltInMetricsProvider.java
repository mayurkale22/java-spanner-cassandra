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
package com.google.cloud.spanner.adapter.metrics;

import static com.google.cloud.opentelemetry.detection.GCPPlatformDetector.SupportedPlatform.GOOGLE_KUBERNETES_ENGINE;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.CLIENT_HASH_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.CLIENT_NAME_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.CLIENT_UID_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.DATABASE_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.INSTANCE_CONFIG_ID_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.INSTANCE_ID_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.LOCATION_ID_KEY;
import static com.google.cloud.spanner.adapter.metrics.BuiltInMetricsConstant.PROJECT_ID_KEY;

import com.google.api.gax.core.GaxProperties;
import com.google.auth.Credentials;
import com.google.cloud.opentelemetry.detection.AttributeKeys;
import com.google.cloud.opentelemetry.detection.DetectedPlatform;
import com.google.cloud.opentelemetry.detection.GCPPlatformDetector;
import com.google.cloud.opentelemetry.metric.GoogleCloudMetricExporter;
import com.google.cloud.opentelemetry.metric.MetricConfiguration;
import com.google.cloud.opentelemetry.metric.MonitoredResourceDescription;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Collects and publishes current values to Google metrics exporter. */
public class BuiltInMetricsProvider {

  public static BuiltInMetricsProvider INSTANCE = new BuiltInMetricsProvider();

  private static final Logger logger = Logger.getLogger(BuiltInMetricsProvider.class.getName());

  private static String taskId;

  private OpenTelemetry openTelemetry;

  private BuiltInMetricsProvider() {}

  public OpenTelemetry getOrCreateOpenTelemetry(
      String projectId, String instanceId, @Nullable Credentials credentials) {
    if (this.openTelemetry != null) {
      return this.openTelemetry;
    }

    SdkMeterProviderBuilder sdkMeterProviderBuilder = SdkMeterProvider.builder();

    MonitoredResourceDescription monitoredResourceDescription =
        new MonitoredResourceDescription(
            BuiltInMetricsConstant.SPANNER_RESOURCE_TYPE,
            ImmutableSet.of(
                "project_id", "instance_id", "location", "instance_config", "client_hash"));

    MetricExporter metricExporter =
        GoogleCloudMetricExporter.createWithConfiguration(
            MetricConfiguration.builder()
                .setProjectId(projectId)
                .setMonitoredResourceDescription(monitoredResourceDescription)
                .setInstrumentationLibraryLabelsEnabled(false)
                .setMetricServiceEndpoint("monitoring.googleapis.com:443")
                .setPrefix(BuiltInMetricsConstant.METER_NAME)
                .setUseServiceTimeSeries(true)
                .setResourceAttributesFilter(MetricConfiguration.NO_RESOURCE_ATTRIBUTES)
                .build());

    sdkMeterProviderBuilder
        .registerMetricReader(
            PeriodicMetricReader.builder(metricExporter)
                .setInterval(java.time.Duration.ofSeconds(60))
                .build())
        .addResource(Resource.create(createResourceAttributes(projectId, instanceId)));

    // Register built-in metrics.
    BuiltInMetricsConstant.getAllViews().forEach(sdkMeterProviderBuilder::registerView);

    SdkMeterProvider sdkMeterProvider = sdkMeterProviderBuilder.build();
    this.openTelemetry = OpenTelemetrySdk.builder().setMeterProvider(sdkMeterProvider).build();
    Runtime.getRuntime().addShutdownHook(new Thread(sdkMeterProvider::close));
    return this.openTelemetry;
  }

  Attributes createResourceAttributes(String projectId, String instanceId) {
    AttributesBuilder attributesBuilder =
        Attributes.builder()
            .put(PROJECT_ID_KEY.getKey(), projectId)
            .put(INSTANCE_CONFIG_ID_KEY.getKey(), "unknown")
            .put(CLIENT_HASH_KEY.getKey(), generateClientHash(getDefaultTaskValue()))
            .put(INSTANCE_ID_KEY.getKey(), instanceId)
            .put(LOCATION_ID_KEY.getKey(), detectClientLocation())
            .put("gcp.resource_type", BuiltInMetricsConstant.SPANNER_RESOURCE_TYPE);

    return attributesBuilder.build();
  }

  public Map<String, String> createDefaultAttributes(String databaseId) {
    Map<String, String> defaultAttributes = new HashMap<>();
    defaultAttributes.put(DATABASE_KEY.getKey(), databaseId);
    defaultAttributes.put(
        CLIENT_NAME_KEY.getKey(),
        "spanner-cassandra-java/" + GaxProperties.getLibraryVersion(getClass()));
    defaultAttributes.put(CLIENT_UID_KEY.getKey(), getDefaultTaskValue());
    return defaultAttributes;
  }

  /**
   * Generates a 6-digit zero-padded all lower case hexadecimal representation of hash of the
   * accounting group. The hash utilizes the 10 most significant bits of the value returned by
   * `Hashing.goodFastHash(64).hashBytes()`, so effectively the returned values are uniformly
   * distributed in the range [000000, 0003ff].
   *
   * <p>The primary purpose of this function is to generate a hash value for the `client_hash`
   * resource label using `client_uid` metric field. The range of values is chosen to be small
   * enough to keep the cardinality of the Resource targets under control. Note: If at later time
   * the range needs to be increased, it can be done by increasing the value of `kPrefixLength` to
   * up to 24 bits without changing the format of the returned value.
   *
   * @return Returns a 6-digit zero-padded all lower case hexadecimal representation of hash of the
   *     accounting group.
   */
  static String generateClientHash(String clientUid) {
    if (clientUid == null) {
      return "000000";
    }

    HashFunction hashFunction = Hashing.goodFastHash(64);
    Long hash = hashFunction.hashBytes(clientUid.getBytes()).asLong();
    // Don't change this value without reading above comment
    int kPrefixLength = 10;
    long shiftedValue = hash >>> (64 - kPrefixLength);
    return String.format("%06x", shiftedValue);
  }

  static String detectClientLocation() {
    GCPPlatformDetector detector = GCPPlatformDetector.DEFAULT_INSTANCE;
    DetectedPlatform detectedPlatform = detector.detectPlatform();
    // All platform except GKE uses "cloud_region" for region attribute.
    String region = detectedPlatform.getAttributes().get("cloud_region");
    if (detectedPlatform.getSupportedPlatform() == GOOGLE_KUBERNETES_ENGINE) {
      region = detectedPlatform.getAttributes().get(AttributeKeys.GKE_CLUSTER_LOCATION);
    }
    return region == null ? "global" : region;
  }

  /**
   * Generates a unique identifier for the Client_uid metric field. The identifier is composed of a
   * UUID, the process ID (PID), and the hostname.
   *
   * <p>For Java 9 and later, the PID is obtained using the ProcessHandle API. For Java 8, the PID
   * is extracted from ManagementFactory.getRuntimeMXBean().getName().
   *
   * @return A unique identifier string in the format UUID@PID@hostname
   */
  private static String getDefaultTaskValue() {
    if (taskId == null) {
      String identifier = UUID.randomUUID().toString();
      String pid = getProcessId();

      try {
        String hostname = InetAddress.getLocalHost().getHostName();
        taskId = identifier + "@" + pid + "@" + hostname;
      } catch (UnknownHostException e) {
        logger.log(Level.INFO, "Unable to get the hostname.", e);
        taskId = identifier + "@" + pid + "@localhost";
      }
    }
    return taskId;
  }

  private static String getProcessId() {
    try {
      // Check if Java 9+ and ProcessHandle class is available
      Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
      Method currentMethod = processHandleClass.getMethod("current");
      Object processHandleInstance = currentMethod.invoke(null);
      Method pidMethod = processHandleClass.getMethod("pid");
      long pid = (long) pidMethod.invoke(processHandleInstance);
      return Long.toString(pid);
    } catch (Exception e) {
      // Fallback to Java 8 method
      final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
      if (jvmName != null && jvmName.contains("@")) {
        return jvmName.split("@")[0];
      } else {
        return "unknown";
      }
    }
  }
}
