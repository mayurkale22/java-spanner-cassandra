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

// package com.google.cloud.spanner.adapter;

// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.verifyNoMoreInteractions;

// import com.google.api.gax.rpc.StatusCode.Code;
// import com.google.api.gax.tracing.MetricsTracer;
// import com.google.common.collect.ImmutableMap;
// import io.opentelemetry.api.OpenTelemetry;
// import io.opentelemetry.api.common.Attributes;
// import io.opentelemetry.api.metrics.LongCounter;
// import io.opentelemetry.api.metrics.LongCounterBuilder;
// import io.opentelemetry.api.metrics.Meter;
// import io.opentelemetry.api.metrics.MeterBuilder;
// import java.util.Map;
// import org.junit.Before;
// import org.junit.Test;
// import org.junit.runner.RunWith;
// import org.junit.runners.JUnit4;
// import org.mockito.Mock;
// import org.mockito.Mockito;

// @RunWith(JUnit4.class)
// public class MetricsRecorderTest {
//   private static final String SERVICE_NAME = "OtelRecorderTest";
//   private static final String OPERATION_COUNT = SERVICE_NAME + "/operation_count";
//   private static final String DEFAULT_METHOD_NAME = "fake_service.fake_method";

//   private MetricsRecorder metricsRecorder;
//   @Mock private OpenTelemetry openTelemetry;
//   @Mock private Meter meter;
//   @Mock private MeterBuilder meterBuilder;
//   @Mock LongCounter operationCountRecorder;
//   @Mock private LongCounterBuilder operationCountRecorderBuilder;

//   @Before
//   public void setUp() {
//     Mockito.when(openTelemetry.meterBuilder(Mockito.anyString())).thenReturn(meterBuilder);
//     Mockito.when(meterBuilder.setInstrumentationVersion(Mockito.anyString()))
//         .thenReturn(meterBuilder);
//     Mockito.when(meterBuilder.build()).thenReturn(meter);
//     // setup mocks for all the recorders using chained mocking
//     setupOperationCountRecorder();

//     metricsRecorder = new MetricsRecorder(openTelemetry, SERVICE_NAME);
//   }

//   private Map<String, String> getAttributes(Code statusCode) {
//     return ImmutableMap.of(
//         "status",
//         statusCode.toString(),
//         "method_name",
//         DEFAULT_METHOD_NAME,
//         "language",
//         MetricsTracer.DEFAULT_LANGUAGE);
//   }

//   @Test
//   public void testOperationCountRecorder_recordsAttributes() {
//     Map<String, String> attributes = getAttributes(Code.OK);

//     Attributes otelAttributes = metricsRecorder.toOtelAttributes(attributes);
//     metricsRecorder.recordOperationCount(1, attributes);

//     verify(operationCountRecorder).add(1, otelAttributes);
//     verifyNoMoreInteractions(operationCountRecorder);
//   }

//   private void setupOperationCountRecorder() {
//     // Configure chained mocking for operationCountRecorder
//
// Mockito.when(meter.counterBuilder(OPERATION_COUNT)).thenReturn(operationCountRecorderBuilder);
//     Mockito.when(operationCountRecorderBuilder.setDescription(Mockito.anyString()))
//         .thenReturn(operationCountRecorderBuilder);
//     Mockito.when(operationCountRecorderBuilder.setUnit("1"))
//         .thenReturn(operationCountRecorderBuilder);
//     Mockito.when(operationCountRecorderBuilder.build()).thenReturn(operationCountRecorder);
//   }
// }
