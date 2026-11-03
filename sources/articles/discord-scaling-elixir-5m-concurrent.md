---
source: How Discord Scaled Elixir to 5,000,000 Concurrent Users
source_url: https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users
author: Stanislav Vishnevskiy
type: article
date_published: 2017-07-06
date_extracted: 2026-08-16
topic: system-design-concepts / tech
---

# How Discord Scaled Elixir to 5,000,000 Concurrent Users

> Faithful reproduction of the Discord engineering blog post by Stanislav Vishnevskiy
> (July 6, 2017), captured as a source extract. Original: [discord.com/blog](https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users).

From the beginning, Discord has been an early adopter of Elixir. The Erlang VM was the perfect candidate for the highly concurrent, real-time system we were aiming to build. We developed the original prototype of Discord in Elixir; that became the foundation of our infrastructure today. Elixir's promise was simple: access the power of the Erlang VM through a much more modern and user-friendly language and toolset.

Fast forward two years, and we are up to nearly five million concurrent users and millions of events per second flowing through the system. While we don't have any regrets with our choice of infrastructure, we did have to do a lot of research and experimentation to get here. Elixir is a new ecosystem, and the Erlang ecosystem lacks information about using it in production (although [Erlang in Anger](https://www.erlang-in-anger.com/) is awesome). What follows is a set of lessons learned and libraries created throughout our journey of making Elixir work for Discord.

## Message Fanout

While Discord is rich with features, most of it boils down to pub/sub. Users connect to a WebSocket and spin up a session process (a `GenServer`), which then communicates with remote Erlang nodes that contain guild (internal for a "Discord Server") processes (also `GenServer`s). When anything is published in a guild, it is fanned out to every session connected to it.

When a user comes online, they connect to a guild, and the guild publishes a presence to all other connected sessions. Guilds have a lot of other logic behind the scenes, but here's a simplified example:

```elixir
def handle_call({:publish, message}, _from, %{sessions: sessions}=state) do
  Enum.each(sessions, &send(&1.pid, message))
  {:reply, :ok, state}
end
```

This was a fine approach when we originally built Discord to groups of 25 or less. However, we have been fortunate enough to have "good problems" arise as people started using Discord for large scale groups. Eventually we ended up with many Discord servers like /r/Overwatch with up to 30,000 concurrent users. During peak hours, we began to see these processes fail to keep up with their message queues. At a certain point, we had to manually intervene and turn off features that generated messages to help cope with the load. We had to figure this out before it became a full-time job.

We began by benchmarking hot paths within the guild processes and quickly stumbled onto an obvious culprit. Sending messages between Erlang processes was not as cheap as we expected, and the reduction cost — Erlang unit of work used for process scheduling — was also quite high. We found that the wall clock time of a single `send/2` call could range from 30μs to 70μs due to Erlang de-scheduling the calling process. This meant that during peak hours, publishing an event from a large guild could take anywhere from 900ms to 2.1s! Erlang processes are effectively single threaded, and the only way to parallelize the work is to shard them. That would have been quite an undertaking, and we knew there had to be a better way.

We knew we had to somehow distribute the work of sending messages. Since spawning processes in Erlang is cheap, our first guess was to just spawn another process to handle each publish. However, each publish could be scheduled at a different time, and Discord clients depend on linearizability of events. That solution also wouldn't scale well because the guild service was also responsible for an ever-growing amount of work.

Inspired by a blog post about [boosting performance of message passing between nodes](https://web.archive.org/web/20170706005541/https://medium.com/@jlouis666/optimizing-a-distributed-erlang-application-with-a-single-line-of-code-9a63c0e5a5f8), **Manifold** was born. Manifold distributes the work of sending messages to the remote nodes of the PIDs (Erlang process identifier), which guarantees that the sending processes at most only calls `send/2` equal to the number of involved remote nodes. Manifold does this by first grouping PIDs by their remote node and then sending to `Manifold.Partitioner` on each of those nodes. The partitioner then consistently hashes the PIDs using `:erlang.phash2/2`, groups them by number of cores, and sends them to child workers. Finally, those workers send the messages to the actual processes. This ensures the partitioner does not get overloaded and still provides the linearizability guaranteed by `send/2`. This solution was effectively a drop-in replacement for `send/2`:

```elixir
Manifold.send([self(), self()], :hello)
```

An awesome side-effect of Manifold was that we were able to not only distribute the CPU cost of fanning out messages, but also reduce the network traffic between nodes (*Network Reduction on 1 Guild Node*).

Manifold is available on our GitHub, so give it a spin. <https://github.com/discordapp/manifold>

## Fast Access Shared Data

Discord is a distributed system achieved through consistent hashing. Using this method requires us to create a ring data structure that can be used to lookup the node of a particular entity. We want that to be fast, so we chose the wonderful library by Chris Moos via an Erlang C port (process responsible for interfacing with C code). It worked great for us, but as Discord scaled, we started to notice issues when we had bursts of users reconnecting. The Erlang process responsible for controlling the ring would start to get so busy that it would fail to keep up with requests to the ring, and the whole system would become overloaded. The solution at first seemed obvious: run multiple processes with the ring data to better utilize all the machine's cores to answer the requests. However, we noticed that this was a hot path. Could we do better?

Let's break down the cost of this hot path.

- A user can be in any number of guilds, but an average user is in 5.
- An Erlang VM responsible for sessions can have up to 500,000 live sessions on it.
- When a session connects, it has to lookup the remote node for each guild it is interested in.
- The cost of communicating with another Erlang process using request/reply is about 12μs.

If the session server were to crash and restart, it would take about 30 seconds just for the cost of lookups on the ring. That does not even account for Erlang de-scheduling the single process involved in the ring for other processes' work. Could we remove this cost completely?

The first thing people do in Elixir when they want to speed up data access is to introduce ETS. ETS is a fast, mutable dictionary implemented in C; the tradeoff is that data is copied in and out of it. We couldn't just move our ring into ETS because we were using a C port to control the ring, so we converted the code to pure Elixir. Once that was implemented, we had a process whose job was to own the ring and constantly copy it into ETS so other processes could read directly from ETS. This noticeably improved performance, but ETS reads were about 7μs, and we were still spending 17.5 seconds on looking up values in the ring. The ring data structure is actually fairly large, and copying it in and out of ETS was the majority of the cost. We were disappointed; in any other language we could easily just have a shared value that was safe to read. There had to be a way to do this in Erlang!

After doing some research, we found `mochiglobal`, a module that exploits a feature of the VM: if Erlang sees a function that always returns the same constant data, it puts that data into a read-only shared heap that processes can access without copying the data. `mochiglobal` takes advantage of this by creating an Erlang module with one function at runtime and compiling it. Since the data is never copied, the lookup cost decreases to 0.3μs, bringing the total time down to 750ms! There's no such thing as a free lunch though; the cost of building a module with a data structure as large as the ring at runtime can take up to a second. The good news is that we rarely change the ring, so it was a penalty we were willing to take.

We decided to port `mochiglobal` to Elixir and add some functionality to avoid creating atoms. Our version is called **FastGlobal** and is available at <https://github.com/discordapp/fastglobal>.

## Limited Concurrency

After solving the performance of the node lookup hot path, we noticed that the processes responsible for handling `guild_pid` lookup on the guild nodes were getting backed up. The inherent back pressure of the slow node lookup had previously protected these processes. The new problem was that nearly 5,000,000 session processes were trying to stampede ten of these processes (one on each guild node). Making this path faster wouldn't solve the problem; the underlying issue was that the call of a session process to this guild registry would timeout and leave the request in the queue of the guild registry. It would then retry the request after a backoff, but perpetually pile up requests and get into an unrecoverable state. Sessions would block on these requests until they timed out while receiving messages from other services, causing them to balloon their message queues and eventually OOM the whole Erlang VM resulting in cascading service outages.

We needed to make session processes smarter; ideally, they wouldn't even try to make these calls to the guild registry if a failure was inevitable. We didn't want to use a circuit breaker because we didn't want a burst in timeouts to result in a temporary state where no attempts are made at all. We knew how we would solve this in other languages, but how would we solve it in Elixir?

In most other languages, we could use an atomic counter to track outstanding requests and bail early if the number was too high, effectively implementing a semaphore. The Erlang VM is built around coordinating through communication between processes, but we knew we didn't want to overload a process responsible for doing this coordination. After some research we stumbled upon `:ets.update_counter/4`, which performs atomic conditional increment operations on a number inside an ETS key. Since we needed high concurrency, we could also run ETS in `write_concurrency` mode but still read the value out, since `:ets.update_counter/4` returns the result. This gave us the fundamental piece to create our **Semaphore** library. It is extremely easy to use and performs really well at high throughput:

```elixir
semaphore_name = :my_sempahore
semaphore_max = 10
case Semaphore.call(semaphore_name, semaphore_max, fn -> :ok end) do
  :ok ->
    IO.puts "success"
  {:error, :max} ->
    IO.puts "too many callers"
end
```

This library has proved instrumental in protecting our Elixir infrastructure. A similar situation to the aforementioned cascading outages occurred as recently as last week, but there were no outages this time. Our presence services crashed due to an unrelated issue, but the session services did not even budge, and the presence services were able to rebuild within minutes after restarting (*Live presences within presence service; CPU usage on the session services around the same time period*).

You can find our Semaphore library on GitHub at <https://github.com/discordapp/semaphore>.

## Conclusion

Choosing to use and getting familiar with Erlang and Elixir has proven to be a great experience. If we had to go back and start over, we would definitely choose the same path. We hope that sharing our experiences and tools proves useful to other Elixir and Erlang developers, and we hope to continue sharing as we progress on our journey, solving problems and learning lessons along the way.

---

## Extract notes (not part of the original article)

Reusable system-design lessons worth surfacing if this is later synthesized via `/update-wiki`:

- **Fan-out cost is real even in "cheap" actor runtimes.** BEAM `send/2` ran 30–70μs and de-schedules the caller; a 30k-member fan-out from one single-threaded process hit 0.9–2.1s. Single-threaded-per-process means the only native parallelism is sharding.
- **Manifold — push fan-out to the destination nodes.** Group PIDs by remote node so the origin makes at most *one* `send` per node; each node's partitioner consistently hashes (`phash2`) across cores. Cuts CPU *and* cross-node network traffic, preserves per-recipient linearizability.
- **Shared read-only data without copy — the constant-function heap trick.** ETS is fast but *copies* in/out (~7μs/read, and huge for a big ring). `mochiglobal`/FastGlobal compile the data into a module constant → VM stores it in a read-only shared heap → 0.3μs reads, no copy. Trade-off: ~1s to rebuild the module on change, so only for rarely-mutated data.
- **Back-pressure via atomic semaphore, not circuit breaker.** When 5M clients stampede 10 registry processes, timeouts queue → retry storm → ballooned mailboxes → OOM → cascading outage. `:ets.update_counter/4` (atomic, `write_concurrency`) gives a lock-free semaphore so callers bail *before* issuing a doomed request — unlike a circuit breaker, it never fully stops attempts.
- **Through-line:** all three fixes are the same move — *don't route a hot path through a single coordinating process*. Distribute the send (Manifold), share the read (FastGlobal), gate the caller locally (Semaphore).

---

# Companion Deep-Dive: Distributed Systems & Erlang Architecture

> Extended notes (not part of the original Discord post) — a series of deep-dives into
> distributed systems architecture, OS primitives, and the Erlang/Elixir (BEAM) VM,
> inspired by the scaling challenges above. Own synthesis; captured here as source
> material for later wiki extraction.

## Contents
1. [Message Routing & Manifold (Scaling Fanout)](#1-message-routing--manifold-scaling-fanout)
2. [Erlang Message Passing vs. JVM (Akka)](#2-erlang-message-passing-vs-jvm-akka)
3. [Global State & Concurrency (ETS vs. FastGlobal)](#3-global-state--concurrency-ets-vs-fastglobal)
4. [OS Primitives: Processes vs. Threads vs. Green Threads](#4-os-primitives-processes-vs-threads-vs-green-threads)
5. [Thundering Herds & Load Shedding (Semaphores)](#5-thundering-herds--load-shedding-semaphores)
6. [Message Durability & Multi-Device Sync (Read State)](#6-message-durability--multi-device-sync-read-state)

## 1. Message Routing & Manifold (Scaling Fanout)

### The Problem
Broadcasting a single chat message to 30,000 connected users across multiple servers typically requires 30,000 individual network calls, causing massive CPU scheduling overhead and network spam.

### The Manifold Solution
Manifold is an architecture pattern that batches and parallelizes message delivery while preserving strict ordering (linearizability).

- **Network Batching (Group by Node):** The sender groups the target Process IDs (PIDs) by their physical destination server (node). It sends one bulk network message per server, reducing network hops drastically.
- **Local Dispatch (The Partitioner):** The receiving server routes the bulk message to a single local dispatcher process (`Manifold.Partitioner`).
- **Parallelization (CPU Core Hashing):** The Partitioner hashes the target PIDs (using `:erlang.phash2/2`) and divides them evenly among local "Worker" processes mapped to the physical CPU cores.
- **Strict Ordering:** Because a single channel process acts as the source of truth, messages are sent chronologically. The deterministic hashing ensures that messages for a specific user (PID) are always routed to the exact same Worker queue. This guarantees messages never leapfrog one another.

## 2. Erlang Message Passing vs. JVM (Akka)

### Erlang/BEAM (Strict Isolation)
Erlang utilizes the **Actor Model**. Every process is isolated with its own tiny heap and garbage collector. There is no shared memory for standard data.

- **Deep Copying:** When Process A sends a message to Process B, the VM executes a low-level C `memcpy` to duplicate the data into Process B's mailbox.
- **Micro-GC:** Because heaps are tiny, Garbage Collection pauses take microseconds and only affect the individual process, avoiding "Stop-the-World" pauses.
- **The "Large Binary" Loophole:** To prevent copying huge payloads (like images or long texts), BEAM places data larger than 64 bytes into a hidden **Shared Heap**. Processes pass lightweight references to this data, and the VM tracks references to garbage collect it when no longer needed.

### JVM & Akka (Shared Memory & Immutability)
Standard Java threads share one massive global heap, requiring locks and mutexes to prevent race conditions. The **Akka framework** mimics Erlang's Actor Model on the JVM.

- **Local Pass-by-Reference:** To avoid the CPU/GC cost of deep-copying large objects in the JVM, Akka passes memory pointers between local actors.
- **The Honor System:** Akka relies on developers using strictly **immutable** objects (e.g., Records, Case Classes). Since neither actor can modify the object, race conditions are avoided without copying.
- **Remote Actors:** If Akka messages cross the network, the framework serializes (deep-copies) the message over TCP, acting identically to Erlang.

## 3. Global State & Concurrency (ETS vs. FastGlobal)

When millions of isolated green threads need to read a massive, shared data structure (like a routing ring), bottlenecks form.

### The Evolution of the Bottleneck

| Phase | Architecture | Bottleneck / Problem |
| --- | --- | --- |
| **1. C Port (IPC)** | External C program connected via Standard I/O. | **Message Queue Bottleneck:** Erlang forces single-process ownership of IPC pipes to prevent data corruption. 500k processes queuing on 1 process took ~30 seconds. |
| **2. Pure Elixir + ETS** | Data moved into Erlang Term Storage (ETS). | **Memory Allocation Bottleneck:** ETS allows concurrent reads, but BEAM must *deep-copy* the data out of ETS into the caller's heap. Copying a massive ring millions of times took ~17.5 seconds. |
| **3. FastGlobal** | Dynamic code generation (Mochiglobal). | **None:** Bypassed memory copying by tricking the VM into compiling the ring data as hardcoded literals in a new module. The VM places code literals in a read-only shared heap accessible by all processes instantly (~0.75 seconds). |

## 4. OS Primitives: Processes vs. Threads vs. Green Threads

Understanding concurrency requires mapping abstract execution units to their OS implementations.

### OS Processes (Heavyweight)
- **State Management:** Tracked by the OS via a Process Control Block (PCB).
- **Memory:** Distinct Virtual Memory Address Space. Requires extensive Page Tables.
- **Context Switch Cost (Highest):** Requires a User-to-Kernel Mode switch. Most critically, it requires a **TLB Flush** (Translation Lookaside Buffer), causing massive cache misses on memory lookups post-switch.
- **Best For:** Strict security sandboxing, fault isolation, and bypassing GILs in scripted languages (e.g., Python `multiprocessing`).

### OS Threads (Medium-weight)
- **State Management:** Tracked via Thread Control Block (TCB).
- **Memory:** Shared virtual memory space within the parent process. However, each thread requires a fixed-size, pre-allocated kernel stack (usually 1MB-2MB).
- **Context Switch Cost (Moderate):** Requires a Mode Switch to Kernel Mode, but **no TLB flush** since the memory space remains the same.
- **Best For:** CPU-bound mathematical operations (mapped 1:1 to physical cores) and blocking Foreign Function Interface (FFI/C++) calls.

### Green Threads (Lightweight / Erlang / Java Loom)
- **State Management:** Tracked entirely in User Space by the language runtime. Uses M:N scheduling (multiplexing M green threads onto N OS threads).
- **Memory:** Dynamically allocated call stacks on the heap (starting at ~300 bytes in Erlang).
- **Context Switch Cost (Lowest):** Zero mode switching. The runtime simply swaps instruction pointers in user space.
- **Best For:** I/O bound tasks, massive concurrency (millions of persistent connections, WebSockets).

## 5. Thundering Herds & Load Shedding (Semaphores)

Removing a performance bottleneck upstream often triggers a **Thundering Herd** downstream, leading to cascading cluster failures.

### The Anatomy of a Stampede
1. A network blip or rolling restart disconnects millions of users.
2. All sessions attempt to look up their new routing simultaneously.
3. The downstream registry bottlenecks. Requests time out (e.g., after 5s) but *stay in the destination mailbox*.
4. Clients automatically retry, multiplying the load exponentially.
5. Session processes, blocked waiting for replies, cannot process their own incoming mailboxes. Memory balloons until the server triggers an Out-Of-Memory (OOM) crash.

### Load Shedding via Local ETS Semaphores
To prevent OOM crashes, the system must **Fail Fast**. Circuit breakers are too aggressive (shutting off all traffic), so a Semaphore is used to shape traffic.

- **Implementation:** An `:ets.update_counter` atomic operation is used. ETS operations are **BIFs (Built-In Functions)** running in user space (taking < 1µs), not expensive OS system calls.
- **Node-Local Logic:** The semaphore lives on the *calling* node (Session Node), strictly limiting concurrent outbound network requests.
- **The Math:** If N processes need to reconnect, and the semaphore allows C concurrent requests, the time to resolve the queue is governed by the Network Round Trip Time (T_trip):

$$\text{Total Recovery Time} = \frac{N}{C} \times T_{trip}$$

  *Example:* 10,000 users / 10 concurrent limit = 1,000 batches. At 2ms per trip, the node safely processes the queue in 2 seconds without ever overwhelming the target server.

## 6. Message Durability & Multi-Device Sync (Read State)

In high-throughput chat applications, in-memory architectures require fallback mechanisms to prevent data loss and phantom notifications.

### Durability Lifecycle
1. **Client Safety Net:** The sending client starts a local retry timer and renders the message optimistically. If the server drops the message, the client will retry.
2. **Database Commit:** The message reaches the Guild Process, which writes it to a durable database (e.g., Cassandra/ScyllaDB).
3. **ACK & Fanout:** Upon DB confirmation, an ACK is sent back to the sender (stopping the retry timer), and the message is fanned out to online users via WebSockets.
4. **Offline Gap Fill:** When an offline user reconnects, their client checks its local cache (e.g., "Last seen message ID: 1005") and pulls missing messages directly from the database REST API, bypassing real-time fanout.

### Resolving Multi-Device Notifications (Watermarking)
To prevent a desktop client from pinging for messages already read on a mobile device, a global **Read State** service is required.

- **The Watermark:** A highly optimized Key-Value store tracks a user's global read position per channel (`Channel X = Message ID 2000`).
- **Reconciliation:** When a client wakes up, it fetches the global Watermark.
- **Silent Insertion:** The client downloads all missing messages to fill its UI gaps. Any message with an ID *older* than the global Watermark is inserted silently. Only messages *newer* than the Watermark trigger a notification badge or sound.

---

## Extract notes — companion deep-dive (for `/update-wiki`)

New concepts this addendum adds beyond the original article, and where each likely lands:

- **Actor-model memory: deep-copy vs shared-heap.** BEAM `memcpy`s messages into the recipient mailbox (isolation → micro-GC, no stop-the-world), *except* binaries >64B go to a ref-counted shared heap. Akka gets the same actor semantics on the JVM but passes *pointers* locally and leans on developer-enforced immutability (the "honor system"); it only deep-copies (serializes) when a message crosses the network. → new `theory/` page (actor-model message passing), links [[theory/copy-on-write-vs-mvcc]].
- **Concurrency primitives ladder: process → thread → green thread.** The real cost axis is the context switch: OS process = mode switch + **TLB flush** (cache-cold after); OS thread = mode switch, no TLB flush, but a fixed ~1–2MB kernel stack; green thread = M:N user-space, ~300B growable stack, no mode switch. Maps "millions of connections" to why BEAM/green-threads win I/O-bound. → new `theory/` page, links existing threads/concurrency material.
- **Thundering herd + recovery-time math.** Recovery time ≈ (N/C) × T_trip once you shed with a node-local semaphore; ETS `update_counter` is a BIF (<1µs, user space), not a syscall. Sharpens the [[system-design-concepts/rate-limiting]] "stampede/back-pressure" story with a closed-form.
- **Durability + watermarking for multi-device.** Optimistic send + client retry timer → DB commit (Cassandra/Scylla) → ACK stops timer → fanout; offline clients gap-fill from a REST/DB read by last-seen ID. A global **Read State watermark** (KV, per-channel read position) makes older-than-watermark inserts silent, only newer ones notify. → new `system-design-concepts/` page (read-state / watermarking), links [[theory/durability-rpo-rto]].
