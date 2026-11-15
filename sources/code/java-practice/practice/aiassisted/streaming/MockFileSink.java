package practice.aiassisted.streaming;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MockFileSink implements ISink {
    private final Random random = new Random();

    private final String path;
    private final double errorRate;
    private final List<String> content;

    public MockFileSink(String path, double rate) {
        this.path = path;
        errorRate = rate;
        content = new ArrayList<>();
    }

    public MockFileSink() throws IOException {
        this(Files.createTempFile("mock_file_sink", ".log").toString(), 0.3);
    }

    @Override 
    public synchronized void write(List<String> lines) throws TransientStorageException {
        if (random.nextDouble() < errorRate) {
            throw new TransientStorageException("Transient error writing to " + path);
        }

        content.addAll(lines);
    }

    @Override
    public String toString() {
        return String.format("MockFileSink with path %s", path);
    }
}
