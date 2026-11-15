# Block-aligned range cache

Original Java sketch.

This is the saved implementation body. The sketch is incomplete and does not compile.

```java
/** Client to download files from the internet. */
interface StorageClient {
    /** Get the size of the file at $uri. */
    long getFileSize(String uri);

    /** Fetch $length bytes at $offset from $uri into $dstBuf */
    void fetch(String uri, long offset, long length, byte[] dstBuf);
}
// /** Thread safe file random IO API. */
// class RandomIoFile(path: String) {
//     def write(offset: Int, srcBuf: Array[Byte]): Unit
//     def read(offset: Int, length: Int, dstBuf: Array[Byte]): Unit
// }



/**
* This class downloads and caches a remote file at $uri for repeated reading.
*/
class CachedFile {
    private static final int CHUNK_SIZE = 1000;

    private String uri;
    private StorageClient client;
    private Set<Integer> cachedChunks;

    private ExecutorService executor = Executors.newFixedThreadPool(10);

    private Map<Integer, Semaphore> locksByChunkId;

    public CachedFile(String uri, StorageClient client) {
        this.uri = uri;
        this.client = client;
        this.cachedChunks = new ConcurrentHashMap<>().getSet();
        locksByChunkId = new ConcurrentHashMap<>();
    }

    /** Read $length bytes at $offset into $dstBuf (as fast as possible). */
    //
    // offset: 1100, length: 3500
    // [1000, 2000), [2000, 3000), [3000, 4000)
    // chunks: [1, 2, 3]
    // 1: [100, 1000)
    // 2: [0, 1000]
    // 3: [0, 500)
    //
    public void read(long offset, long length, byte[] dstBuf) {
      List<Integer> chunksToRead = mappedChunks(offset, length);
      List<Future> futures = new ArrayList<>();
        int i = 0;
        for (int chunkId : chunksToRead) {
          int offsetWithinChunk = i == 0 ? Math.max(0, offset - chunkId * CHUNK_SIZE) : 0;
          int lengthToRead = i == chunksToRead.size() - 1 ? length - chunkId * CHUNK_SIZE : CHUNK_SIZE;
          long bufferOffset = i * CHUNK_SIZE;
          Future<Void> chunkFuture = executor.submit(() -> readAChunk(chunkId, offsetWithinChunk, lengthToRead, dstBuf, bufferOffset));
          futures.add(chunkFuture);
        }
      }

      // wait on all futures to finish
      waitAll(futures);
    }

    private void readAChunk(int chunkId, int offsetWithinChunk, int lengthToRead, byte[] dstBuf, long bufferOffset) {
      if (cachedChunks.containsKey(chunkId)) {
            readFromChunk(chunkId, offsetWithinChunk, lengthToRead, dstBuf, bufferOffset);
            return;
      }

      Semaphore chunkMutex = locksByChunkId.computeIfAbsent(chunkId, k -> new Semaphore(1));
      try {
        chunkMutex.acquire();
        fetchAndCacheChunk(chunkId, offsetWithinChunk, lengthToRead, dstBuf, bufferOffset);
      } finally {
        chunkMutex.release();
      }
    }

    /**
     * offset: 1310
     * length: 10000
     * [1310, 11310)
     */
    private List<Integer> mappedChunks(long offset, long length) {
      int chunkId = offset/CHUNK_SIZE;
      List<Integer> chunks = new ArrayList<>();
      do {
        chunks.add(chunkId);
        length -= CHUNK_SIZE;
        chunkId++;
      } while(length > 0);

      return chunks;
    }

    // read a cached chunk into dstBuf
    private void readFromChunk(int chunkId, int offsetWithinChunk, int length, byte[] dstBuf, int offset) {
      int offsetInFile = chunkId * CHUNK_SIZE + offsetWithinChunk;
      // read from local disk, and put into dstBuf starting from given offset
      read(offsetInFile, length, dstBuf, offset);
    }

    // fetch from storage and put in dstBuf, and also cache it locally
    private void fetchAndCacheChunk(int chunkId, int offsetWithinChunk, int length, byte[] dstBuf, int offset) {
      int offsetInRemoteFile = chunkId * CHUNK_SIZE + offsetWithinChunk;

      // read from remote storage
      client.fetch(uri, offsetInRemoteFile, length, dstBuf, offset);

      // cache to local file
      write(offsetInRemoteFile, dstBuf, offset);
      cachedChunks.put(chunkId);
    }
}
```
