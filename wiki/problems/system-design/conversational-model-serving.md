# Conversational model serving

## Problem

Serve multi-turn conversations and stream each answer as it is generated. Many open connections compete for limited inference capacity.

## Example

A gateway keeps a client connection open while a different machine generates tokens. Queueing the prompt creates a return-path problem: each token must reach the gateway holding that connection.

## Recorded approach

Hold the client stream open for token delivery and queue requests when generation capacity is exhausted. The discussion surfaced the need to route queued responses back to the waiting connection.

[Anonymized candidate design notes](../../../sources/notes/designs/conversational-model-serving.md)

## Retrospective study notes

Distilled from a Staff-level 45-min mock systems-design interview: **"Design ChatGPT"**, scoped to the multi-turn conversation loop (client → LLM → streamed response → client). This is the **cloud-thin, consumer-chat** sibling of [local terminal coding agent](local-terminal-coding-agent.md) — same base prompt ("design an AI conversation chatbot"), opposite fork.

### Key Ideas

- **The crux is a constrained, expensive resource, not throughput.** Unlike WhatsApp/Discord (tens of millions of *dumb* msgs/s on commodity boxes), every message here **occupies a GPU for seconds**, streams token-by-token, and runs on hardware you *cannot cheaply overprovision*. The whole design flows from: long-held + streaming + on a scarce resource. Naming this correctly is 50% of the question.
- **Read/write symmetry is unusual.** Traditional web apps are read-heavy; here every user turn is a write *and* triggers an expensive generation, so in ≈ out. Provisioning intuition from CRUD apps doesn't transfer.
- **Little's Law sizes the connection tier.** 2M req/s × ~5s held = ~10M concurrent connections → ~100 edge boxes at 100K conns/box. This part is genuinely easy and commodity — the trap is spending time here instead of on the GPU tier.
- **A queue smooths bursts; it CANNOT rescue a sustained capacity deficit.** If arrival (2M/s) >> service (finite GPUs) *on average*, a durable queue just grows to hours of backlog and everyone times out. The only real levers are **add capacity / shed load / degrade quality**. "Add Kafka" is not an answer to a 1000× gap.
- **Decoupling the request breaks the return path.** Once a durable queue sits between edge and GPU worker, the worker no longer knows *which edge box holds the user's socket*. You need a **session registry + pub/sub back-channel** (edge subscribes to `stream:{session_id}`, worker publishes tokens there). Enqueue the *request* durably; stream *tokens* over a lighter real-time channel — never push the token stream through Kafka.
- **Two paths, two postures.** Durable log (requests, completed turns, training/history feed — reused by other consumers) vs. real-time token channel (fast, lossy-but-reconstructable). Conflating them into "one Kafka" is a smell.
- **The cost model lives in the GPU tier.** KV cache (every turn re-attends the growing context; memory, not FLOPs, often bounds you) and **continuous batching** (many sequences per GPU) are what actually set capacity/QPS.

### My Understanding

- The conversation loop is the core; profile/history/settings are parkable metadata. Saying "the GPU fleet is the bottleneck" is only half the answer — the design has to open that box (batching, KV cache, admission), not treat it as a black box.
- A queue is a *buffer for variance around a serviceable mean*. If the mean isn't serviceable (a 1000× arrival-vs-service gap), queuing just converts "drop requests" into "make everyone wait forever," which is worse. When arrival >> service, the answer is admission control, priority, and a degradation path. (Sizing check: 20B messages/day ÷ ~100k s/day ≈ 200K QPS average.)
- Introducing a queue to decouple edge from GPU *destroys* the direct relationship that made streaming easy. The session registry keyed by `session_id` + pub/sub is the box that restores it. Any decoupling owes a story of how the response finds its way home.
- Offline inbox / resume-on-another-device is real (it's the same [read state watermarking](../../system-design-concepts/read-state-watermarking.md) / [message fanout](../../system-design-concepts/message-fanout.md) machinery from Discord), but it comes after the *online* streaming path — depth on the happy path first.
- Numbers have to *drive*, not decorate: "10K served" next to "10M arriving" is a 1000× ratio that should dictate the next design move.
- **30-second answer for "why is this different from WhatsApp?":** "A human message is delivered in milliseconds and the server holds nothing. A generated response holds an expensive GPU open for seconds while it streams — so the design is about protecting and scheduling a scarce compute resource, not about moving bytes."

### Open Questions

- **KV cache sizing:** how do I compute KV-cache memory per sequence (layers × heads × head_dim × 2 × context_len × dtype) and therefore how many concurrent sequences fit on one GPU? (→ kipply "Transformer Inference Arithmetic".)
- **Continuous vs static batching:** mechanically, how does in-flight batching admit/evict sequences mid-generation, and why is the throughput win ~10–20×? (→ Anyscale continuous batching.)
- **PagedAttention:** how does treating KV cache like OS paged virtual memory raise batch size / reduce fragmentation? (→ vLLM.)
- **Admission control design:** priority queues for paid vs free, per-user concurrency caps, and *when* to fall back to a smaller model — what's the actual policy and where does it live (edge? scheduler?)?
- **GPU scheduler:** is the "consumer" really a scheduler that packs sequences into batches by SLA? What does the queue→batch handoff look like in practice?

### Connections

- Sibling fork of: [local terminal coding agent](local-terminal-coding-agent.md) — same base prompt, cloud/consumer vs local/coding.
- Relates to: [discord scaling elixir 5m concurrent](../../reading-notes/discord-scaling-elixir-5m-concurrent.md) — the 10M-concurrent-connection edge tier, message fanout, and offline inbox/resume are the same machinery ([message fanout](../../system-design-concepts/message-fanout.md), [read state watermarking](../../system-design-concepts/read-state-watermarking.md)).
- Uses: [consistent hashing](../../theory/consistent-hashing.md) — session→edge-box routing and migration on failure.
- Sizing tool shared with: [distributed persistent key value store](distributed-persistent-key-value-store.md) — Little's Law (`concurrency = throughput × latency`) for the connection/fleet estimate.
- Nuances: [rate limiting](../../system-design-concepts/rate-limiting.md) / [rate limiting algorithms](../../theory/rate-limiting-algorithms.md) — load-shedding/admission-control here is rate limiting applied to a scarce *compute* resource, not an API quota.
- Grounded by: [latency numbers](../../theory/latency-numbers.md) — why ms-scale message delivery and s-scale generation are different regimes.

### Concepts flagged for wiki (pending /update-wiki)
- Constrained-resource serving & GPU-fleet economics (queue vs. capacity vs. load-shed).
- LLM inference internals: KV cache, continuous batching, memory-bandwidth roofline.
- Streaming return path: session registry + pub/sub back-channel.
- Durable log vs. real-time channel separation.
- Admission control / graceful degradation under sustained overload.
