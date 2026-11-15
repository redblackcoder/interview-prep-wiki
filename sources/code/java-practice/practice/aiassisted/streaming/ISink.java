package practice.aiassisted.streaming;

import java.util.List;

public interface ISink {
    void write(List<String> lines) throws TransientStorageException;
}
