# The Log: What every software engineer should know about real-time data's unifying abstraction

**Author:** Jay Kreps
**Published:** December 16, 2013
**Source:** LinkedIn Engineering blog (original URL not included in the source paste; widely mirrored)

> Saved article (verbatim capture, web UI chrome and inline diagram images removed; figure captions kept as prose). The foundational "log as a unifying abstraction" essay by a Kafka co-creator. Ties together [kafka 101 bytebytego](../../wiki/reading-notes/kafka-101-bytebytego.md), [how to beat the cap theorem](../../wiki/reading-notes/how-to-beat-the-cap-theorem.md), and [streaming 101 world beyond batch](../../wiki/reading-notes/streaming-101-world-beyond-batch.md).

---

I joined LinkedIn about six years ago at a particularly interesting time. We were just beginning to run up against the limits of our monolithic, centralized database and needed to start the transition to a portfolio of specialized distributed systems. This has been an interesting experience: we built, deployed, and run to this day a distributed graph database, a distributed search backend, a Hadoop installation, and a first and second generation key-value store.

One of the most useful things I learned in all this was that many of the things we were building had a very simple concept at their heart: the log. Sometimes called write-ahead logs or commit logs or transaction logs, logs have been around almost as long as computers and are at the heart of many distributed data systems and real-time application architectures.

You can't fully understand databases, NoSQL stores, key value stores, replication, paxos, hadoop, version control, or almost any software system without understanding logs; and yet, most software engineers are not familiar with them. I'd like to change that. In this post, I'll walk you through everything you need to know about logs, including what is log and how to use logs for data integration, real time processing, and system building.

## Part One: What Is a Log?

A log is perhaps the simplest possible storage abstraction. It is an **append-only, totally-ordered sequence of records ordered by time.** Records are appended to the end of the log, and reads proceed left-to-right. Each entry is assigned a unique sequential log entry number.

The ordering of records defines a notion of "time" since entries to the left are defined to be older than entries to the right. The log entry number can be thought of as the "timestamp" of the entry. Describing this ordering as a notion of time seems a bit odd at first, but it has the convenient property that it is **decoupled from any particular physical clock.** This property will turn out to be essential as we get to distributed systems.

The contents and format of the records aren't important for the purposes of this discussion. Also, we can't just keep adding records to the log as we'll eventually run out of space (more on that later).

So, a log is not all that different from a file or a table. A file is an array of bytes, a table is an array of records, and a log is really just a kind of table or file where the records are sorted by time.

At this point you might be wondering why it is worth talking about something so simple? The answer is that logs have a specific purpose: **they record what happened and when.** For distributed data systems this is, in many ways, the very heart of the problem.

Every programmer is familiar with another definition of logging — the unstructured error messages or trace info an application might write out to a local file using syslog or log4j. For clarity I will call this "application logging". The application log is a degenerative form of the log concept I am describing. The biggest difference is that text logs are meant to be primarily for humans to read, and the "journal" or "data logs" I'm describing are built for programmatic access.

### Logs in databases

The usage in databases has to do with keeping in sync the variety of data structures and indexes in the presence of crashes. To make this atomic and durable, a database uses a log to write out information about the records it will be modifying, before applying the changes to all the various data structures it maintains. **The log is the record of what happened, and each table or index is a projection of this history into some useful data structure or index.** Since the log is immediately persisted, it is used as the authoritative source in restoring all other persistent structures in the event of a crash.

Over time the usage of the log grew from an implementation detail of ACID to a method for replicating data between databases. It turns out that the sequence of changes that happened on the database is exactly what is needed to keep a remote replica database in sync. Oracle, MySQL, and PostgreSQL include log shipping protocols to transmit portions of log to replica databases which act as slaves.

Because of this origin, the concept of a machine readable log has largely been confined to database internals. The use of logs as a mechanism for data subscription seems to have arisen almost by chance. But this very abstraction is ideal for supporting all kinds of messaging, data flow, and real-time data processing.

### Logs in distributed systems

The two problems a log solves — **ordering changes and distributing data** — are even more important in distributed data systems.

The log-centric approach to distributed systems arises from a simple observation that I will call the **State Machine Replication Principle**:

> If two identical, deterministic processes begin in the same state and get the same inputs in the same order, they will produce the same output and end in the same state.

**Deterministic** means that the processing isn't timing dependent and doesn't let any other "out of band" input influence its results (e.g. a program influenced by thread execution order or a call to `gettimeofday` is non-deterministic). The **state** of the process is whatever data remains on the machine at the end of the processing. The bit about getting the same input in the same order is where the log comes in: if you feed two deterministic pieces of code the same input log, they will produce the same output.

The application to distributed computing is pretty obvious. You can reduce the problem of making multiple machines all do the same thing to the problem of implementing a distributed consistent log to feed these processes input. **The purpose of the log here is to squeeze all the non-determinism out of the input stream** to ensure that each replica processing this input stays in sync.

One of the beautiful things about this approach is that the timestamps that index the log now act as the clock for the state of the replicas — you can describe each replica by a single number, the timestamp for the maximum log entry it has processed.

There are a multitude of ways of applying this principle depending on what is put in the log: log the incoming requests, or the state changes the service undergoes, or the transformation commands it executes.

Database people generally differentiate between **physical logging** (logging the contents of each row that is changed) and **logical logging** (logging the SQL commands — insert/update/delete — that lead to the row changes).

The distributed systems literature distinguishes two broad approaches. The **"state machine model"** (active-active) keeps a log of the incoming requests and each replica processes each request. The **"primary-backup model"** elects one replica as leader, lets it process requests in arrival order and log out the resulting state changes; other replicas apply those state changes in order.

To understand the difference, consider a replicated "arithmetic service" holding a single number (init 0) applying additions and multiplications. The active-active approach logs the transformations ("+1", "*2"); each replica applies them. The active-passive approach has a single master execute the transformations and log the results ("1", "3", "6"). This also makes clear why **ordering is key**: reordering an addition and a multiplication yields a different result.

The distributed log can be seen as the data structure which models the problem of **consensus**. A log represents a series of decisions on the "next" value to append. You have to squint to see a log in the Paxos family (via "multi-paxos", which models the log as a series of consensus problems, one per slot), but the log is much more prominent in protocols like **ZAB, RAFT, and Viewstamped Replication**, which directly model the problem of maintaining a distributed, consistent log.

In reality, the consensus problem is a bit too simple. Computer systems rarely need to decide a single value; they almost always handle a *sequence* of requests. So a log, rather than a simple single-value register, is the more natural abstraction. I suspect we will end up focusing more on the log as a **commoditized building block** irrespective of its implementation — much as we talk about a hash table without specifying the exact variant.

### Changelog 101: Tables and Events are Dual

There is a fascinating **duality between a log of changes and a table.** The log is similar to the list of all credits and debits a bank processes; a table is all the current account balances. If you have a log of changes, you can apply these changes in order to create the table capturing the current state. **The log is the more fundamental data structure**: in addition to creating the original table you can transform it to create all kinds of derived tables.

This works in reverse too: if you have a table taking updates, you can record these changes and publish a "changelog" of all the updates. This changelog is exactly what you need to support near-real-time replicas. So **tables and events are dual: tables support data at rest, and logs capture change.** A complete log of changes holds not only the final version of the table but also allows recreating every previous state — effectively a backup of every previous state of the table.

This might remind you of source-code version control, which also models a sequence of patches (a log); you interact with a checked-out snapshot (the table), and replication happens via the log (you pull down patches and apply them).

At the core of these problems is the ability to have many machines play back history at their own rate in a deterministic manner. The usefulness of the log comes from a simple function: **producing a persistent, re-playable record of history.**

## Part Two: Data Integration

> Data integration is making all the data an organization has available in all its services and systems.

The more recognizable term ETL usually covers only a limited part of data integration — populating a relational data warehouse. Much of what I describe can be thought of as ETL generalized to cover real-time systems and processing flows.

Effective use of data follows a kind of Maslow's hierarchy of needs. The base of the pyramid involves capturing all the relevant data and modeling it in a uniform way; only once that's done is it reasonable to work on more sophisticated processing (MapReduce, real-time query, visualization, prediction). Most organizations have huge holes in the base of this pyramid — they lack reliable complete data flow — but want to jump directly to advanced data modeling. This is completely backwards.

**Two trends make data integration harder:**

1. **The event data firehose.** Event data records things that *happen* rather than things that *are* — user activity logging, machine-level events and stats. This data is at the heart of the modern web (Google's fortune is a relevance pipeline built on clicks and impressions) and tends to be orders of magnitude larger than traditional database uses.
2. **The explosion of specialized data systems** — OLAP, search, online storage, batch processing, graph analysis, and so on.

### Log-structured data flow

The log is the natural data structure for handling data flow between systems. The recipe is very simple:

> **Take all the organization's data and put it into a central log for real-time subscription.**

Each logical data source is modeled as its own log (an application logging events, or a database table accepting modifications). Each subscribing system reads from this log as quickly as it can, applies each new record to its own store, and advances its position in the log. Subscribers can be any kind of data system — a cache, Hadoop, another database, a search system.

The log gives a **logical clock** for each change against which all subscribers can be measured; each has a "point in time" it has read up to. (E.g., to avoid stale reads after writing log entry X, don't read from any cache that hasn't replicated up to X.)

The log also acts as a **buffer** that makes data production asynchronous from consumption. A subscriber can crash or go down for maintenance and catch up when it comes back, consuming at a pace it controls. Neither the originating source nor the log knows about the destination systems, so consumers can be added and removed with no change to the pipeline.

> "Each working data pipeline is designed like a log; each broken data pipeline is broken in its own way." — Count Leo Tolstoy (translation by the author)

I use the term **"log"** instead of "messaging system" or "pub sub" because it is a lot more specific about semantics. "Publish subscribe" doesn't imply much more than indirect addressing of messages. You can think of the log as a messaging system with **durability guarantees and strong ordering semantics.** In distributed systems this model of communication sometimes goes by the (somewhat terrible) name **atomic broadcast.**

### At LinkedIn

As LinkedIn moved from a centralized relational database to a collection of distributed systems (Search, Social Graph, Voldemort, Espresso, Recommendations, OLAP, Hadoop, Teradata, monitoring), the data integration problem emerged in fast-forward.

Connecting every system to every other directly leads to O(N²) pipelines — an army of people to build and never operable. Instead, we needed something generic: **isolate each consumer from the source of the data** by having everything integrate with a single central pipeline. Adding a new data system should create integration work to connect it to *one* pipeline, not to each other system.

This experience led me to focus on building **Kafka** to combine what we'd seen in messaging systems with the log concept popular in databases and distributed-system internals. (Amazon later offered a very similar service, Kinesis — right down to partitioning, retention, and the high/low-level consumer split. "A sign you've created a good infrastructure abstraction is that AWS offers it as a service!")

### Relationship to ETL and the Data Warehouse

A data warehouse containing clean, integrated data is a phenomenal asset, but having a *batch* system be the only repository of clean complete data means the data is unavailable for systems requiring a real-time feed. ETL is really two things: (1) an extraction and cleanup process that liberates data and removes system-specific nonsense, and (2) restructuring that data for warehousing queries. **Conflating these two is a problem.** The clean, integrated repository should be available in real-time too.

A better approach: a central pipeline (the log) with a well-defined API for adding data. **The responsibility of providing a clean, well-structured feed lies with the producer of the data** (who knows it best). This is far more organizationally scalable than a central warehouse team responsible for cleaning everyone else's data.

This raises options for where cleanup/transformation lives:

- Done by the **data producer** prior to adding data to the log (best: ensure canonical form; logic here should be **lossless and reversible**).
- Done as a **real-time transformation** on the log (producing a new, derived log — e.g. sessionization).
- Done as part of the **load process** into a destination system (only the aggregation specific to that system — e.g. a star/snowflake schema).

### Log Files and Events

This architecture enables **decoupled, event-driven systems.** Consider showing a job posting. Naively, the job page accretes logic for Hadoop/warehouse export, scraping detection, poster analytics, impression capping, recommendation popularity, etc. — and the systems become intertwined.

The event-driven style: the job page just shows the job and **records the fact that a job was shown** with relevant attributes. Each interested system (recommendations, security, analytics, warehouse) subscribes to the feed and does its own processing. The display code needn't know about these systems, and needn't change when a new consumer is added.

### Building a Scalable Log

Using a log as a universal integration mechanism is an elegant fantasy unless the log is fast, cheap, and scalable. At LinkedIn we run over 60 billion unique message writes through Kafka per day (several hundred billion counting cross-datacenter mirroring). A few tricks:

- **Partitioning the log.** The log is chopped into partitions; each partition is a totally ordered log, but there is no global order across partitions. Assignment to a partition is controllable by the writer (usually by key, e.g. user id). Partitioning allows appends without coordination between shards and lets throughput scale linearly with cluster size. Each partition is replicated across N replicas, one of which is leader.
- **Batching** reads and writes into larger, high-throughput operations — from client to server, in disk writes, in replication, in transfer to consumers, and in acknowledging committed data.
- **Avoiding needless data copies** via a simple binary format maintained across in-memory log, on-disk log, and network transfer (enabling zero-copy).

The cumulative effect: you can read and write at the rate supported by the disk or network, even with data sets that vastly exceed memory.

## Part Three: Logs & Real-time Stream Processing

It turns out that **"log" is another word for "stream"** and logs are at the heart of stream processing.

I see stream processing broadly: **infrastructure for continuous data processing.** The computational model can be as general as MapReduce, but with the ability to produce low-latency results. The real driver is the method of data collection: **data collected in batch is naturally processed in batch; data collected continuously is naturally processed continuously.**

(The US census is the extreme batch example — brute-force door-to-door enumeration made sense in 1790 when data collection meant riding around on horseback. Today you'd keep a journal of births and deaths and produce counts continuously.) Many "batch" jobs that run daily are effectively mimicking continuous computation with a window size of one day.

Seen this way, **stream processing is a generalization of batch processing** that includes a notion of time in the underlying data and doesn't require a static snapshot — it can produce output at a user-controlled frequency instead of waiting for the "end" of the data set. The traditional view of stream processing as niche was due to a lack of real-time data collection (which is also what doomed the early commercial stream-processing systems — their customers were still doing daily batch ETL). Finance, where real-time streams were already the norm, was the one domain where stream processing found early success.

### Data flow graphs

Stream processing extends the idea of a data feed: feeds can be **computed off other feeds.** These derived feeds look no different to consumers than primary-data feeds. A stream processing job is anything that reads from logs and writes output to logs or other systems; the logs join these processes into a **graph of processing stages.**

The purpose of the log in this integration is two-fold:

1. It makes each dataset **multi-subscriber and ordered.** (Reordering two updates to the same record can produce the wrong output. This ordering is more permanent than TCP's — it isn't limited to a single point-to-point link and survives process failures and reconnections.)
2. It provides **buffering.** If an upstream job produces faster than a downstream job consumes, processing must block, buffer, or drop. The log acts as a very large buffer that lets processes restart or fail without slowing other parts of the graph — critical isolation when many teams' jobs share the flow. We cannot have one faulty job cause back-pressure that stops the entire flow.

### Stateful Real-Time Processing

Many uses are more than stateless record-at-a-time transforms — counts, aggregations, or joins over windows (e.g. enriching a click stream by joining to the user account database). This requires state. How to keep it correct if processors can fail?

Keeping state only in memory loses it on crash. Storing all state in a remote system means no locality and lots of network round-trips. The answer comes from the **table/log duality**: a stream processor keeps its state in a **local "table"/"index"** (a BDB, LevelDB, even a Lucene index), fed from its input streams, and **journals out a changelog** of that local index so it can restore state after a crash/restart. When the process fails, it restores its index from the changelog.

This has the elegant property that **the state of the processors is itself maintained as a log** — so other processors can subscribe to it. Combined with the change-logs coming out of databases, the power of the log/table duality becomes clear.

### Log Compaction

We can't keep a complete log for all state changes for all time. In Kafka, cleanup has two options:

- For **event data**: retain a window of data (defined in time or space — usually a few days).
- For **keyed data**: instead of throwing away the old log, remove **obsolete records** — records whose primary key has a more recent update. This still guarantees the log contains a complete backup of the source system (though you can only recreate recent states, not all previous ones). This feature is called **log compaction.**

## Part Four: System Building

There's an analogy between the role a log serves for data flow inside a distributed database and the role it serves for data integration across an organization. In both cases it's responsible for data flow, consistency, and recovery. What, after all, is an organization if not a very complicated distributed data system?

### Unbundling?

If you squint, you can see the whole of your organization's systems and data flows as a **single distributed database.** Query-oriented systems (Redis, SOLR, Hive tables) are just particular *indexes* on your data; stream processing systems (Storm, Samza) are just a well-developed *trigger and view materialization* mechanism.

My take is that the explosion of different systems is caused by the difficulty of building distributed data systems. By cutting scope to a single query type, each system becomes feasible to build — but running all of them yields too much complexity. Three possible futures:

1. **Status quo** — separation of systems persists; the data integration problem (and an external integrating log) remains centrally important.
2. **Re-consolidation** — a single general uber-system merges the functions back together (I think the practical difficulties make this unlikely).
3. **Unbundling** (the one I find appealing) — data infrastructure unbundled into a collection of reusable, open-source building blocks: ZooKeeper for coordination; Mesos/YARN for resource management; Lucene/LevelDB for indexing; Netty/Jetty/Finagle for communication; Avro/Protocol Buffers/Thrift for serialization; **Kafka/BookKeeper for a backing log.** Stack these and it looks like a Lego version of distributed data system engineering.

### The place of the log in system architecture

A system that assumes an external log is present lets individual systems relinquish much of their own complexity. Things a log can do:

- Handle **data consistency** (eventual or immediate) by sequencing concurrent updates to nodes
- Provide **data replication** between nodes
- Provide **"commit" semantics** to the writer (acknowledge only when the write is guaranteed not to be lost)
- Provide the **external data subscription feed** from the system
- Provide the capability to **restore failed replicas** or bootstrap new ones
- Handle **rebalancing** of data between nodes

This is a substantial portion of what a distributed data system does. What's left over is mostly the client-facing **query API and indexing strategy** — exactly the part that should vary from system to system.

**How it works:** split the system into two logical pieces — **the log** and **the serving layer.** The log captures state changes in sequential order. Serving nodes store whatever index is required to serve queries (btree/sstable for a KV store, an inverted index for search). Writes go to the log (possibly proxied by the serving layer), yielding a logical timestamp. Log and serving nodes have the same number of partitions (though possibly different machine counts). Serving nodes subscribe to the log and apply writes to their local index in log order.

A client can get **read-your-write** semantics from any node by providing the write's timestamp in its query — the node delays the request until it has indexed up to at least that time. The serving nodes may need **no notion of leader election at all**, since the log is the source of truth. Restoring a failed node or moving a partition is handled by replaying the log (plus a snapshot).

It's worth noting that although Kafka and BookKeeper are consistent logs, this is not a requirement: you could factor a Dynamo-like database into an **eventually-consistent AP log** and a key-value serving layer (such a log redelivers old messages and depends on the subscriber to handle it, like Dynamo itself).

Having a separate (even complete) copy of data in the log strikes many as wasteful, but the log is a particularly efficient storage mechanism (linear reads/writes → cheap multi-TB HDDs), and its cost is amortized across the multiple indexes/serving systems it feeds. This is exactly the pattern LinkedIn used to build search, social graph, and OLAP query systems: none need an externally accessible write API — Kafka and databases are the system of record, and changes flow to the query systems through the log.

## References (selected)

Academic papers, systems, talks:

- State machine & primary-backup replication overviews; **PacificA** (Microsoft's log-based storage framework).
- **Spanner** — Google's database that uses *physical* time and models clock-drift uncertainty (timestamp as a range) rather than logical time.
- Rich Hickey's *Datomic / Deconstructing the Database* talk.
- *A Survey of Rollback-Recovery Protocols in Message-Passing Systems.*
- **Paxos**: Lamport's original; his *Paxos Made Simple*; Google's *Paxos Made Live* (Chubby); **John Ousterhout's video** presentations (which make consensus far clearer drawn as communication rounds).
- Paxos alternatives closer to log implementation: **Viewstamped Replication** (Liskov), **ZAB** (ZooKeeper), **RAFT** (Ousterhout — an attempt at a more understandable consensus algorithm; the video is great too).
- Logs in real databases: **PNUTS, HBase, Bigtable, Espresso** (LinkedIn — uses the table itself as the source of the log).
- Stream processing: *Models and Issues in Data Stream Systems*; **Discretized Streams** (Spark Streaming); **MillWheel** (Google); **Naiad** (timely dataflow).
- Enterprise-software cognates: **Event Sourcing** (≈ state machine replication), **Change Data Capture**, **Enterprise Service Bus**, **Complex Event Processing**.
- Open source: **Kafka**, **BookKeeper/Hedwig**, **Databus**, **Akka (eventsourced)**, **Samza**, **Storm**, **Spark Streaming**, **Summingbird**.
