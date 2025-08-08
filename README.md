# Cassandra TCP Interceptor (Proxy)

A small Java library that starts a Netty-based TCP proxy to intercept Cassandra native protocol traffic and log it. It forwards traffic transparently to a target Cassandra node.

## Features

- Intercepts Cassandra native protocol frames (v3/v4)
- Logs frame headers (version, flags, stream id, opcode) and a configurable preview of the body (hex)
- Transparent TCP proxy between client and real Cassandra node
- Simple start/stop API

## Getting started

Add the dependency (build from source for now):

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>cassandra-proxy</artifactId>
  <version>0.1.0</version>
</dependency>
```

Ensure you also have an SLF4J binding on the classpath (e.g., `slf4j-simple` or `logback-classic`).

### Example usage

```java
import com.example.cassandra.proxy.CassandraProxyServer;

public class Example {
  public static void main(String[] args) {
    CassandraProxyServer server = CassandraProxyServer
        .newBuilder()
        .listenHost("0.0.0.0")
        .listenPort(9042)
        .targetHost("127.0.0.1")  // real Cassandra host
        .targetPort(9042)
        .bodyPreviewBytes(256)     // how many bytes of the body to log
        .build();

    server.startBlocking();

    // ... keep running ...

    Runtime.getRuntime().addShutdownHook(new Thread(server::stopBlocking));
  }
}
```

## Notes

- This proxy logs frame metadata and a hex preview of the body. It does not fully parse CQL payloads.
- Point clients at the proxy's listen host/port instead of the Cassandra node to intercept traffic.
- For production usage, consider tuning Netty options and adding metrics.
