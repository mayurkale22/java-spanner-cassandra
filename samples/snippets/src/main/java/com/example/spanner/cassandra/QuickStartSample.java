/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.spanner.cassandra;

// [START spanner_cassandra_quick_start]

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.cloud.spanner.adapter.SpannerCqlSession;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

// This sample assumes your spanner database <my_db> contains a table <users>
// with the following schema:

// CREATE TABLE users (
//  id        INT64          OPTIONS (cassandra_type = 'int'),
//  active    BOOL           OPTIONS (cassandra_type = 'boolean'),
//  username  STRING(MAX)    OPTIONS (cassandra_type = 'text'),
// ) PRIMARY KEY (id);

class QuickStartSample {

  public static void main(String[] args) {

    // TODO(developer): Replace these variables before running the sample.
    final String projectId = "span-cloud-testing";
    final String instanceId = "c2sp";
    final String databaseId = "analytics";

    final String databaseUri =
        String.format("projects/%s/instances/%s/databases/%s", projectId, instanceId, databaseId);

    try (CqlSession session =
        SpannerCqlSession.builder() // `SpannerCqlSession` instead of `CqlSession`
            .setDatabaseUri(databaseUri) // Set spanner database URI.
            .addContactPoint(new InetSocketAddress("localhost", 9044))
            .withLocalDatacenter("datacenter1")
            .setBuiltInMetricsEnabled(true)
            .withKeyspace(databaseId) // Keyspace name should be the same as spanner database name
            .withConfigLoader(
                DriverConfigLoader.programmaticBuilder()
                    .withString(DefaultDriverOption.PROTOCOL_VERSION, "V4")
                    .withDuration(
                        DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, Duration.ofSeconds(5))
                    .build())
            .build()) {

      PreparedStatement insertStatement =
          session.prepare("INSERT INTO usertable (id, field0) VALUES (?, ?)");
      int i = 0;
      while (true) {
        System.out.println(Instant.now() + " : Starting request");
        BoundStatement boundInsert = insertStatement.bind("hello" + i++, "Mayur");
        session.execute(boundInsert);
        System.out.println(Instant.now() + " : Finished request");

        try {
          if (i % 10 == 0) {
            session.prepare("INSERT INTO usertable1 (id, field0) VALUES (?, ?) USING TTL 0");
          }
        } catch (Exception e) {
          System.out.println("query failed");
        }

        try {
          Thread.sleep(10000); // Sleep for 2 seconds to avoid busy-waiting
        } catch (InterruptedException e) {
          System.out.println("Application interrupted.");
          Thread.currentThread().interrupt(); // Restore the interrupted status
          break; // Exit the loop if interrupted
        }
      }

      System.out.println("Hello");
      while (true) {
        // Your application logic goes here
        // System.out.println("Application is running... " + System.currentTimeMillis());
        try {
          Thread.sleep(2000); // Sleep for 2 seconds to avoid busy-waiting
        } catch (InterruptedException e) {
          System.out.println("Application interrupted.");
          Thread.currentThread().interrupt(); // Restore the interrupted status
          break; // Exit the loop if interrupted
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

// [END spanner_cassandra_quick_start]
