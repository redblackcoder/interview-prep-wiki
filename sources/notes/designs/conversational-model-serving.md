# Conversational model serving

Anonymized candidate design notes.

Client sends the message via a streaming HTTP connection (the request carries the conversation; a streaming response returns tokens one by one). The connection from client → middleware stays open ~5s (avg generation duration).

Treat the generator fleet as a black box that handles, say, ~10,000 concurrent. Huge mismatch: 10M in vs 10K served. If requests just park in the kernel TCP buffer, boxes OOM and crash. Introduce a **persisted queue (Kafka)**: requests enqueue; the fleet consumes at its capacity (~10K/s). During peak, user response time degrades but the system doesn't fall over.
