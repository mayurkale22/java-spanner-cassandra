package com.example.cassandra.proxy;

final class CqlUtils {

    private CqlUtils() {}

    static String opcodeToString(int opcode) {
        return switch (opcode) {
            case 0x00 -> "ERROR";
            case 0x01 -> "STARTUP";
            case 0x02 -> "READY";
            case 0x03 -> "AUTHENTICATE";
            case 0x05 -> "OPTIONS";
            case 0x06 -> "SUPPORTED";
            case 0x07 -> "QUERY";
            case 0x08 -> "RESULT";
            case 0x09 -> "PREPARE";
            case 0x0A -> "EXECUTE";
            case 0x0B -> "REGISTER";
            case 0x0C -> "EVENT";
            case 0x0D -> "BATCH";
            case 0x0E -> "AUTH_CHALLENGE";
            case 0x0F -> "AUTH_RESPONSE";
            case 0x10 -> "AUTH_SUCCESS";
            default -> "OP_0x" + Integer.toHexString(opcode).toUpperCase();
        };
    }
}