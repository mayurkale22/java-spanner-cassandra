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

package com.google.cloud.spanner.adapter.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.cloud.spanner.adapter.AbstractIT;
import com.google.cloud.spanner.adapter.utils.ColumnDefinition;
import com.google.cloud.spanner.adapter.utils.DatabaseContext;
import com.google.cloud.spanner.adapter.utils.TableDefinition;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class TimestampIT extends AbstractIT {

  private static final String TABLE_NAME = "timestamp_test";
  private static PreparedStatement insertStatement;
  private static PreparedStatement selectByIdStatement;
  private static PreparedStatement selectByTimeRangeStatement;

  public TimestampIT(DatabaseContext db) {
    super(db);
  }

  @Rule public TestName name = new TestName();

  @Before
  public void createTable() throws Exception {
    Map<String, ColumnDefinition> testColumns = new HashMap<>();
    testColumns.put("event_id", new ColumnDefinition("STRING(MAX)", "uuid", true));
    testColumns.put("event_name", new ColumnDefinition("STRING(MAX)", "text", false));
    testColumns.put("event_time", new ColumnDefinition("TIMESTAMP", "timestamp", false));
    testColumns.put("event_source", new ColumnDefinition("STRING(MAX)", "text", false));
    TableDefinition timestamp_test = new TableDefinition(TABLE_NAME, testColumns);
    db.createTables(timestamp_test);

    // Prepare statements once
    insertStatement =
        db.getSession()
            .prepare(
                "INSERT INTO "
                    + TABLE_NAME
                    + " (event_id, event_name, event_time, event_source) VALUES (?, ?, ?, ?)");
    selectByIdStatement =
        db.getSession()
            .prepare(
                "SELECT event_id, event_name, event_time, event_source FROM "
                    + TABLE_NAME
                    + " WHERE event_id = ?");
    selectByTimeRangeStatement =
        db.getSession()
            .prepare(
                "SELECT event_id, event_name, event_time, event_source FROM "
                    + TABLE_NAME
                    + " WHERE event_time >= ? AND event_time <= ? ALLOW FILTERING");
  }

  @Test
  public void testInsertAndRetrieveTimestamp() {
    UUID eventId = UUID.randomUUID();
    String eventName = "UserLoggedIn";
    Instant eventTime =
        Instant.now().truncatedTo(ChronoUnit.MILLIS); // Truncate for exact comparison
    String eventSource = "WebUI";

    // Insert data directly
    BoundStatement boundInsert = insertStatement.bind(eventId, eventName, eventTime, eventSource);
    db.getSession().execute(boundInsert);

    // Retrieve data directly
    BoundStatement boundSelect = selectByIdStatement.bind(eventId);
    Row row = db.getSession().execute(boundSelect).one();

    assertThat(row).isNotNull();
    assertThat(eventId).isEqualTo(row.getUuid("event_id"));
    assertThat(eventName).isEqualTo(row.getString("event_name"));
    assertThat(eventSource).isEqualTo(row.getString("event_source"));
    assertThat(eventTime).isEqualTo(row.getInstant("event_time"));
  }

  @Test
  public void testTimestampPrecision() {
    UUID eventId = UUID.randomUUID();
    String eventName = "PrecisionTest";
    Instant originalTime = Instant.parse("2025-05-28T23:59:59.123456789Z"); // High precision
    String eventSource = "Test";

    BoundStatement boundInsert =
        insertStatement.bind(eventId, eventName, originalTime, eventSource);
    db.getSession().execute(boundInsert);

    Row row = db.getSession().execute(selectByIdStatement.bind(eventId)).one();
    assertNotNull(row);
    Instant retrievedTime = row.getInstant("event_time");

    // Cassandra's TIMESTAMP resolution is milliseconds.
    // So, nanoseconds will be truncated.
    Instant expectedTime = originalTime.truncatedTo(ChronoUnit.MILLIS);

    assertThat(expectedTime).isEqualTo(retrievedTime);
    assertThat(retrievedTime.toEpochMilli())
        .isEqualTo(originalTime.toEpochMilli()); // Millis should match
  }

  @Test
  public void testQueryByTimestampRange() throws InterruptedException {
    // Create some events with varying timestamps
    UUID eventId1 = UUID.randomUUID();
    UUID eventId2 = UUID.randomUUID();
    UUID eventId3 = UUID.randomUUID();
    UUID eventId4 = UUID.randomUUID();

    // Use sleep to ensure distinct timestamps if not generating them explicitly
    Instant time1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    TimeUnit.MILLISECONDS.sleep(10); // Ensure times are distinct
    Instant time2 = time1.plusMillis(50);
    TimeUnit.MILLISECONDS.sleep(10);
    Instant time3 = time1.plusMillis(100);
    TimeUnit.MILLISECONDS.sleep(10);
    Instant time4 = time1.plusMillis(150);

    db.getSession().execute(insertStatement.bind(eventId1, "EventA", time1, "Source1"));
    db.getSession().execute(insertStatement.bind(eventId2, "EventB", time2, "Source2"));
    db.getSession().execute(insertStatement.bind(eventId3, "EventC", time3, "Source3"));
    db.getSession().execute(insertStatement.bind(eventId4, "EventD", time4, "Source4"));

    // Query for events within a range
    Instant queryStart = time1.plusMillis(20); // From time2
    Instant queryEnd = time1.plusMillis(120); // Up to time3

    // Note: ALLOW FILTERING is used because event_time is not the partition key
    // and we are doing a range query without the partition key.
    // In a real application, you'd design your primary key to avoid ALLOW FILTERING.
    BoundStatement boundSelectRange = selectByTimeRangeStatement.bind(queryStart, queryEnd);
    ResultSet rs = db.getSession().execute(boundSelectRange);

    List<Row> rows = rs.all();
    assertThat(2).isEqualTo(rows.size()); // Expecting EventB and EventC
  }

  @Test
  public void testNullTimestamp() {
    UUID eventId = UUID.randomUUID();
    String eventName = "NullTest";
    String eventSource = "System";

    // Insert with a null timestamp
    BoundStatement boundInsert = insertStatement.bind(eventId, eventName, null, eventSource);
    db.getSession().execute(boundInsert);

    Row row = db.getSession().execute(selectByIdStatement.bind(eventId)).one();
    assertThat(row).isNotNull();
    assertThat(eventName).isEqualTo(row.getString("event_name"));
    assertThat(row.getInstant("event_time")).isNull();
  }
}
