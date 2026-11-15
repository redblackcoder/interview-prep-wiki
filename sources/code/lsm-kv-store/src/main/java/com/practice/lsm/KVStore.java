package com.practice.lsm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KVStore {
    static enum Op {
        SET,
        DELETE
    }

    private final Wal wal;
    private Map<String, String> memTable;

    public KVStore(String pathStr) {
        memTable = new ConcurrentHashMap<>();
        wal = new Wal(Path.of(pathStr));
    }

    public KVStore() {
        memTable = new ConcurrentHashMap<>();
        wal = new Wal();
    }

    public void restore() throws IOException {
        Map<String, String> newMemTable = new ConcurrentHashMap<>();
        List<String> walLines = wal.readAll();
        walLines.forEach(l -> {
            Map.Entry<Op, String[]> e = deser(l);
            switch (e.getKey()) {
                case SET:
                    newMemTable.put(e.getValue()[0], e.getValue()[1]);
                    break;
                case DELETE:
                    newMemTable.remove(e.getValue()[0]);
                    break;
            }
        });

        memTable = newMemTable;
    }

    public void close() {
        wal.close();
    }

    public void set(String key, String val) throws IOException {
        validate(key);
        validate(val);
        wal.appendLine(ser(Op.SET.name(), key, val));
        memTable.put(key, val);
    }

    public String get(String key) {
        validate(key);
        return memTable.get(key);
    }

    public void delete(String key) throws IOException {
        validate(key);
        wal.appendLine(ser(Op.DELETE.name(), key, null));
        memTable.remove(key);
    }

    private static String ser(String op, String key, String val) {
        if (val != null) {
            return String.format("%s:%s=%s", op, key, val);
        } else {
            return String.format("%s:%s", op, key);
        }
    }

    private static Map.Entry<Op, String[]> deser(String stored) {
        String[] opAndRest = stored.split(":", 2);
        if (opAndRest.length != 2 || opAndRest[0].length() == 0 || opAndRest[1].length() == 0) {
            throw new IllegalArgumentException(String.format("stored value is invalid: %s", stored));
        }

        Map.Entry<Op, String[]> res;
        switch (opAndRest[0]) {
            case "SET":
                res = Map.entry(Op.SET, new String[2]);
                String[] splits = opAndRest[1].split("=");
                if (splits.length != 2 || splits[0].length() == 0 || splits[1].length() == 0) {
                    throw new IllegalArgumentException(String.format("stored value is invalid: %s", stored));
                }

                res.getValue()[0] = splits[0];
                res.getValue()[1] = splits[1];
                break;
            case "DELETE":
                res = Map.entry(Op.DELETE, new String[] { opAndRest[1] });
                break;
            default:
                throw new IllegalArgumentException("Unknown op: " + opAndRest[0]);
        }

        return res;
    }

    private static void validate(String s) throws IllegalArgumentException {
        if (s == null || s.length() == 0 || s.contains("=")) {
            throw new IllegalArgumentException(String.format("String is invalid: %s", s == null ? "NULL" : s));
        }
    }
}
