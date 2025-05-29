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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.cloud.spanner.adapter.AbstractIT;
import com.google.cloud.spanner.adapter.utils.ColumnDefinition;
import com.google.cloud.spanner.adapter.utils.DatabaseContext;
import com.google.cloud.spanner.adapter.utils.TableDefinition;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class SetCollectionIT extends AbstractIT {

  private static final String TABLE_NAME = "user_preferences";
  private static CqlSession session;

  // Prepared Statements
  private static PreparedStatement insertStatement;
  private static PreparedStatement selectStatement;
  private static PreparedStatement updateAddStatement;
  private static PreparedStatement updateRemoveStatement;
  private static PreparedStatement updateSetStatement;

  public SetCollectionIT(DatabaseContext db) {
    super(db);
    session = db.getSession();
  }

  @Rule public TestName name = new TestName();

  @Before
  public void createTable() throws Exception {
    Map<String, ColumnDefinition> testColumns = new HashMap<>();
    testColumns.put("user_id", new ColumnDefinition("STRING(MAX)", "uuid", true));
    testColumns.put("username", new ColumnDefinition("STRING(MAX)", "text", false));
    testColumns.put(
        "favorite_genres", new ColumnDefinition("ARRAY<STRING(MAX)>", "set<text>", false));
    testColumns.put("last_updated", new ColumnDefinition("TIMESTAMP", "timestamp", false));
    TableDefinition set_test = new TableDefinition(TABLE_NAME, testColumns);
    db.createTables(set_test);

    // Prepare statements once
    insertStatement =
        session.prepare(
            "INSERT INTO "
                + TABLE_NAME
                + " (user_id, username, favorite_genres, last_updated) VALUES (?, ?, ?, ?)");
    selectStatement =
        session.prepare(
            "SELECT user_id, username, favorite_genres, last_updated FROM "
                + TABLE_NAME
                + " WHERE user_id = ?");
    updateAddStatement =
        session.prepare(
            "UPDATE "
                + TABLE_NAME
                + " SET favorite_genres = favorite_genres + ? WHERE user_id = ?");
    updateRemoveStatement =
        session.prepare(
            "UPDATE "
                + TABLE_NAME
                + " SET favorite_genres = favorite_genres - ? WHERE user_id = ?");
    updateSetStatement =
        session.prepare(
            "UPDATE "
                + TABLE_NAME
                + " SET favorite_genres = ?, last_updated = ? WHERE user_id = ?");
  }

  @Test
  public void testInsertAndRetrieveSet() {
    UUID userId = UUID.randomUUID();
    String username = "johndoe";
    Set<String> genres = new HashSet<>(Arrays.asList("Sci-Fi", "Fantasy", "Action"));
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    BoundStatement boundInsert = insertStatement.bind(userId, username, genres, now);
    session.execute(boundInsert);

    Row row = session.execute(selectStatement.bind(userId)).one();
    assertThat(row).isNotNull();
    assertThat(userId).isEqualTo(row.getUuid("user_id"));
    assertThat(username).isEqualTo(row.getString("username"));
    assertThat(now).isEqualTo(row.getInstant("last_updated"));

    Set<String> retrievedGenres = row.getSet("favorite_genres", String.class);
    assertNotNull(retrievedGenres);
    assertThat(retrievedGenres).isNotNull();
    assertThat(genres.size()).isEqualTo(retrievedGenres.size());
    assertThat(retrievedGenres).containsAll(genres);
    assertThat(genres).containsAll(retrievedGenres);
  }

  @Test
  public void testUpdateAddElementsToSet() {
    UUID userId = UUID.randomUUID();
    String username = "janedoe";
    Set<String> initialGenres = new HashSet<>(Arrays.asList("Comedy", "Drama"));
    Instant initialTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    session.execute(insertStatement.bind(userId, username, initialGenres, initialTime));

    // Add a single element
    Set<String> newGenre1 = new HashSet<>(Arrays.asList("Thriller"));
    session.execute(updateAddStatement.bind(newGenre1, userId));

    Row row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
    Set<String> updatedGenres = row.getSet("favorite_genres", String.class);
    assertEquals(3, updatedGenres.size());
    assertTrue(updatedGenres.contains("Comedy"));
    assertTrue(updatedGenres.contains("Drama"));
    assertTrue(updatedGenres.contains("Thriller"));

    // Add multiple elements
    Set<String> newGenres2 = new HashSet<>(Arrays.asList("Horror", "Mystery"));
    session.execute(updateAddStatement.bind(newGenres2, userId));

    row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
    updatedGenres = row.getSet("favorite_genres", String.class);
    assertEquals(5, updatedGenres.size());
    assertTrue(
        updatedGenres.containsAll(
            Arrays.asList("Comedy", "Drama", "Thriller", "Horror", "Mystery")));
  }

  @Test
  public void testUpdateRemoveElementsFromSet() {
    UUID userId = UUID.randomUUID();
    String username = "bobsmith";
    Set<String> initialGenres = new HashSet<>(Arrays.asList("Rock", "Pop", "Jazz", "Classical"));
    Instant initialTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    session.execute(insertStatement.bind(userId, username, initialGenres, initialTime));

    // Remove a single element
    Set<String> removeGenre1 = new HashSet<>(Arrays.asList("Pop"));
    session.execute(updateRemoveStatement.bind(removeGenre1, userId));

    Row row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
    Set<String> updatedGenres = row.getSet("favorite_genres", String.class);
    assertEquals(3, updatedGenres.size());
    assertFalse(updatedGenres.contains("Pop"));
    assertTrue(updatedGenres.containsAll(Arrays.asList("Rock", "Jazz", "Classical")));

    // Remove multiple elements
    Set<String> removeGenres2 = new HashSet<>(Arrays.asList("Jazz", "Rock"));
    session.execute(updateRemoveStatement.bind(removeGenres2, userId));

    row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
    updatedGenres = row.getSet("favorite_genres", String.class);
    assertEquals(1, updatedGenres.size());
    assertTrue(updatedGenres.contains("Classical"));
    assertFalse(updatedGenres.contains("Rock"));
    assertFalse(updatedGenres.contains("Jazz"));
  }

  @Test
  public void testUpdateOverwriteEntireSet() {
    UUID userId = UUID.randomUUID();
    String username = "alicebrown";
    Set<String> initialGenres = new HashSet<>(Arrays.asList("Old1", "Old2"));
    Instant initialTime = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

    session.execute(insertStatement.bind(userId, username, initialGenres, initialTime));

    Set<String> newGenres = new HashSet<>(Arrays.asList("NewA", "NewB", "NewC"));
    Instant newTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    session.execute(updateSetStatement.bind(newGenres, newTime, userId));

    Row row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
    Set<String> retrievedGenres = row.getSet("favorite_genres", String.class);
    assertEquals(newGenres.size(), retrievedGenres.size());
    assertTrue(retrievedGenres.containsAll(newGenres));
    assertTrue(newGenres.containsAll(retrievedGenres));
    assertEquals(newTime, row.getInstant("last_updated")); // Verify other column also updated
  }

  @Test
  public void testInsertWithEmptySet() {
    UUID userId = UUID.randomUUID();
    String username = "emptyuser";
    Set<String> emptyGenres = new HashSet<>();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    session.execute(insertStatement.bind(userId, username, emptyGenres, now));

    Row row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
    Set<String> retrievedGenres = row.getSet("favorite_genres", String.class);
    assertNotNull(retrievedGenres);
    assertTrue(retrievedGenres.isEmpty());
  }

  @Test
  public void testInsertWithNullSet() {
    UUID userId = UUID.randomUUID();
    String username = "nulluser";
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // Binding null for the SET column
    BoundStatement boundInsert = insertStatement.bind(userId, username, null, now);
    session.execute(boundInsert);

    Row row = session.execute(selectStatement.bind(userId)).one();
    assertNotNull(row);
  }
}
