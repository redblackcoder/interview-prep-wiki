package com.practice.lsm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class Wal {
    private final Path path;
    private final FileChannel channel;
    private boolean initialized;

    private static Path createTemp() {
        try {
            return Files.createTempFile("com-db-lsm", ".wal");
        } catch (IOException ioe) {
            System.err.printf("Error creating temp file for wal.\n%s\n", ioe.getMessage());
            return null;
        }
    }

    public Wal() {
        this(createTemp());
    }

    public Wal(Path path) {
        this.path = path;
        channel = init(path);
        initialized = channel != null;
    }

    public void close() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ioe) {
                // Ignore
            }
        }
    }

    private static FileChannel init(Path path) {
        if (path == null) {
            return null;
        }

        try {
            var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return channel;
        } catch (IOException ioe) {
            System.err.printf("Error creating channel for file.\n%s\n", ioe.getMessage());
            return null;
        }
    }

    public synchronized void appendLine(String op) throws IOException {
        if (!initialized) {
            throw new IllegalStateException("Wal is not initialized correctly.");
        }

        channel.write(ByteBuffer.wrap((op + "\n").getBytes(StandardCharsets.UTF_8)));
        channel.force(true);
    }

    public List<String> readAll() throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }
}
