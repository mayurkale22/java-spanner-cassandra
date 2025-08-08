package com.example.cassandra.proxy;

public class ExampleMain {
    public static void main(String[] args) {
        String listenHost = System.getProperty("listen.host", "0.0.0.0");
        int listenPort = Integer.getInteger("listen.port", 9042);
        String targetHost = System.getProperty("target.host", "127.0.0.1");
        int targetPort = Integer.getInteger("target.port", 9042);
        int preview = Integer.getInteger("preview", 256);

        CassandraProxyServer server = CassandraProxyServer
                .newBuilder()
                .listenHost(listenHost)
                .listenPort(listenPort)
                .targetHost(targetHost)
                .targetPort(targetPort)
                .bodyPreviewBytes(preview)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stopBlocking));
        server.startBlocking();
    }
}