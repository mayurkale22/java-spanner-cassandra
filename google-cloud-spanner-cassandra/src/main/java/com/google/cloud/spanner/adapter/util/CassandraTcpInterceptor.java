package com.google.cloud.spanner.adapter.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple TCP server to intercept and log Cassandra TCP requests.
 */
public class CassandraTcpInterceptor {
    private static final Logger logger = Logger.getLogger(CassandraTcpInterceptor.class.getName());
    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService executorService;

    public CassandraTcpInterceptor(int port) {
        this.port = port;
    }

    /**
     * Starts the TCP server in a new thread.
     */
    public void start() {
        running = true;
        executorService = Executors.newCachedThreadPool();
        new Thread(this::runServer, "CassandraTcpInterceptor-Main").start();
        logger.info("Cassandra TCP Interceptor started on port " + port);
    }

    /**
     * Stops the TCP server and releases resources.
     */
    public void stop() {
        running = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing server socket", e);
            }
        }
        logger.info("Cassandra TCP Interceptor stopped.");
    }

    private void runServer() {
        try (ServerSocket server = new ServerSocket(port)) {
            this.serverSocket = server;
            while (running) {
                try {
                    Socket client = server.accept();
                    executorService.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.WARNING, "Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start TCP server on port " + port, e);
        }
    }

    private void handleClient(Socket client) {
        String clientInfo = client.getRemoteSocketAddress().toString();
        logger.info("Accepted connection from " + clientInfo);
        try (InputStream in = client.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                // Log the raw bytes as hex for inspection
                logger.info("Received from " + clientInfo + ": " + bytesToHex(buffer, read));
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed or error for " + clientInfo, e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing client socket", e);
            }
        }
    }

    private static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x ", bytes[i]));
        }
        return sb.toString();
    }
}