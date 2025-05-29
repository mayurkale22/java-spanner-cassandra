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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.cloud.spanner.adapter.AbstractIT;
import com.google.cloud.spanner.adapter.utils.ColumnDefinition;
import com.google.cloud.spanner.adapter.utils.DatabaseContext;
import com.google.cloud.spanner.adapter.utils.TableDefinition;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class CounterIT extends AbstractIT {

  private static final String TABLE_NAME = "counter_test";

  public CounterIT(DatabaseContext db) {
    super(db);
  }

  @Rule public TestName name = new TestName();

  @Before
  public void createTable() throws Exception {
    Map<String, ColumnDefinition> testColumns = new HashMap<>();
    testColumns.put("id", new ColumnDefinition("STRING(MAX)", "text", true));
    testColumns.put("views", new ColumnDefinition("INT64", "counter", false));
    TableDefinition counter_test = new TableDefinition(TABLE_NAME, testColumns);
    db.createTables(counter_test);
  }

  private void incrementViews(String id, long amount) {
    db.getSession()
        .execute(
            update(db.keyspace(), TABLE_NAME)
                .increment("views", literal(amount))
                .where(column("id").isEqualTo(literal(id)))
                .build());
  }

  private void decrementViews(String id, long amount) {
    db.getSession()
        .execute(
            "UPDATE " + TABLE_NAME + " SET views = views - " + amount + " WHERE id = '" + id + "'");
  }

  private long getViews(String id) {
    ResultSet rs =
        db.getSession()
            .execute(
                selectFrom(db.keyspace(), TABLE_NAME)
                    .column("views")
                    .where(column("id").isEqualTo(literal(id)))
                    .build());
    Row row = rs.one();
    return row != null
        ? row.getLong("views")
        : 0; // If row is null, counter hasn't been initialized (0 views)
  }

  @Test
  public void testInitialCounterValueIsZero() {
    String articleId = "article_1";
    assertThat(0).isEqualTo(getViews(articleId));
  }

  @Test
  public void testIncrementCounter() {
    // CqlSession session = db.getSession();
    String articleId = "article_2";
    incrementViews(articleId, 1);
    assertThat(1).isEqualTo(getViews(articleId));

    incrementViews(articleId, 5);
    assertThat(6).isEqualTo(getViews(articleId));
  }

  @Test
  public void testDecrementCounter() {
    // CqlSession session = db.getSession();
    String articleId = "article_3";
    incrementViews(articleId, 10);
    assertThat(10).isEqualTo(getViews(articleId));

    decrementViews(articleId, 3);
    assertThat(7).isEqualTo(getViews(articleId));

    decrementViews(articleId, 10);
    assertThat(-3).isEqualTo(getViews(articleId));
  }

  @Test
  public void testMultipleIncrementsAndDecrements() {
    String articleId = "article_4";
    incrementViews(articleId, 100);
    decrementViews(articleId, 20);
    incrementViews(articleId, 5);
    decrementViews(articleId, 30);
    assertEquals(55, getViews(articleId));
  }

  @Test
  public void testCountersForDifferentIdsAreIndependent() {
    String articleId1 = "article_5";
    String articleId2 = "article_6";

    incrementViews(articleId1, 10);
    incrementViews(articleId2, 25);

    assertEquals(10, getViews(articleId1));
    assertEquals(25, getViews(articleId2));

    incrementViews(articleId1, 5);
    assertEquals(15, getViews(articleId1));
    assertEquals(25, getViews(articleId2));
  }
}
