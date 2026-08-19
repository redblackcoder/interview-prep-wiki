---
source: docs/design-chatgpt-mock-interview.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/design-chatgpt-mock-interview.md
type: doc
date_extracted: 2026-08-19
topic: system-design-concepts
---

# Design ChatGPT — Mock Interview

Distilled from a Meta Staff (E6/E7) 45-min systems-design screen: **"Design ChatGPT"**, scoped to the multi-turn conversation loop (client → LLM → streamed response → client). This is the **cloud-thin, consumer-chat** sibling of [[sources/docs/local-coding-agent-system-design]] — same base prompt ("design an AI conversation chatbot"), opposite fork. Interviewer's grade: **6/10**.

## Key Ideas

- **The crux is a constrained, expensive resource, not throughput.** Unlike WhatsApp/Discord (tens of millions of *dumb* msgs/s on commodity boxes), every message here **occupies a GPU for seconds**, streams token-by-token, and runs on hardware you *cannot cheaply overprovision*. The whole design flows from: long-held + streaming + on a scarce resource. Naming this correctly is 50% of the question.
- **Read/write symmetry is unusual.** Traditional web apps are read-heavy; here every user turn is a write *and* triggers an expensive generation, so in ≈ out. Provisioning intuition from CRUD apps doesn't transfer.
- **Little's Law sizes the connection tier.** 2M req/s × ~5s held = ~10M concurrent connections → ~100 edge boxes at 100K conns/box. This part is genuinely easy and commodity — the trap is spending time here instead of on the GPU tier.
- **A queue smooths bursts; it CANNOT rescue a sustained capacity deficit.** If arrival (2M/s) >> service (finite GPUs) *on average*, a durable queue just grows to hours of backlog and everyone times out. The only real levers are **add capacity / shed load / degrade quality**. "Add Kafka" is not an answer to a 1000× gap.
- **Decoupling the request breaks the return path.** Once a durable queue sits between edge and GPU worker, the worker no longer knows *which edge box holds the user's socket*. You need a **session registry + pub/sub back-channel** (edge subscribes to `stream:{session_id}`, worker publishes tokens there). Enqueue the *request* durably; stream *tokens* over a lighter real-time channel — never push the token stream through Kafka.
- **Two paths, two postures.** Durable log (requests, completed turns, training/history feed — reused by other consumers) vs. real-time token channel (fast, lossy-but-reconstructable). Conflating them into "one Kafka" is a smell.
- **The cost model lives in the GPU tier I skipped.** KV cache (every turn re-attends the growing context; memory, not FLOPs, often bounds you) and **continuous batching** (many sequences per GPU) are what actually set capacity/QPS. Couldn't discuss these — the knowledge gap that capped the score.

## My Understanding

- My instinct to name the conversation loop as the core and park profile/history/settings was right, and the read/write-symmetry observation landed. But I stopped at the exact spot where the question gets interesting: I said "the GPU fleet is the bottleneck" and then treated it as a black box. Identifying the crux without opening it is only half-credit — the interviewer's whole back half was trying to get me inside that box, and I couldn't go.
- My biggest *conceptual* error wasn't the dropped 10× (20B/100k = 200K, not 20K — sloppy, but arithmetic). It was believing a **queue could absorb a 1000× arrival-vs-service gap**. I now hold the rule firmly: a queue is a *buffer for variance around a serviceable mean*. If the mean isn't serviceable, queuing just converts "drop requests" into "make everyone wait forever," which is worse. The correct reflex when I see arrival >> service is to say out loud: "I can't buffer this — I need admission control, priority, and a degradation path."
- I hand-waved the token return path ("the consumer opens a connection back to the middleware"). The moment I introduced a queue to decouple edge from GPU, I *destroyed* the direct relationship that made streaming easy — and then pretended it still existed. The fix (session registry keyed by `session_id` + pub/sub) is the missing box. Lesson: when I decouple two things, I owe the interviewer the story of how the response finds its way home.
- The offline-inbox / resume-on-another-device instinct was good and is real (it's the same [[wiki/system-design-concepts/read-state-watermarking]] / [[wiki/system-design-concepts/message-fanout]] machinery from Discord), but I reached for it before I'd nailed the *online* streaming path. Right idea, wrong order — depth on the happy path first.
- Numbers have to *drive*, not decorate. I put "10K served" and "10M arriving" side by side without flinching at the ratio. A staff candidate flinches at the ratio and lets it dictate the next design move.

## Interview-Craft Lessons

- **Open the black box you just named as the crux.** If I say "X is the bottleneck," the interviewer will spend the rest of the round inside X. Refusing to enter it ("I don't work with GPUs") caps the score no matter how clean the rest is. Have a working mental model of the hard subsystem *before* the interview.
- **After every estimate, ask "what does this number force me to do?"** A 1000× gap should immediately trigger load-shedding/degradation, not more buffering.
- **When you decouple, narrate the return path.** Any time a queue or async hop enters the diagram, immediately answer "how does the result get back to the exact caller?"
- **Don't hand-wave a factor of 10.** Fleet sizing compounds estimation errors; the interviewer read the slip as "numbers aren't load-bearing for this candidate."
- **Breadth is a comfort zone under pressure.** When pushed on the hard tier, I widened into ops/privacy. Resist the drift to easy, familiar boxes; stay in the uncomfortable one.
- **30-second answer for "why is this different from WhatsApp?":** "A human message is delivered in milliseconds and the server holds nothing. A generated response holds an expensive GPU open for seconds while it streams — so the design is about protecting and scheduling a scarce compute resource, not about moving bytes."

## Open Questions

- **KV cache sizing:** how do I compute KV-cache memory per sequence (layers × heads × head_dim × 2 × context_len × dtype) and therefore how many concurrent sequences fit on one GPU? (→ kipply "Transformer Inference Arithmetic".)
- **Continuous vs static batching:** mechanically, how does in-flight batching admit/evict sequences mid-generation, and why is the throughput win ~10–20×? (→ Anyscale continuous batching.)
- **PagedAttention:** how does treating KV cache like OS paged virtual memory raise batch size / reduce fragmentation? (→ vLLM.)
- **Admission control design:** priority queues for paid vs free, per-user concurrency caps, and *when* to fall back to a smaller model — what's the actual policy and where does it live (edge? scheduler?)?
- **GPU scheduler:** is the "consumer" really a scheduler that packs sequences into batches by SLA? What does the queue→batch handoff look like in practice?

## Connections

- Sibling fork of: [[sources/docs/local-coding-agent-system-design]] — same base prompt, cloud/consumer vs local/coding. Same recurring anti-pattern (relabeling the hard core into an easier neighbor: "serve the model" → "manage connections/queues").
- Relates to: [[sources/articles/discord-scaling-elixir-5m-concurrent]] — the 10M-concurrent-connection edge tier, message fanout, and offline inbox/resume are the same machinery ([[wiki/system-design-concepts/message-fanout]], [[wiki/system-design-concepts/read-state-watermarking]]).
- Uses: [[wiki/theory/consistent-hashing]] — session→edge-box routing and migration on failure.
- Sizing tool shared with: [[sources/docs/distributed-kv-store-mock-interview]] — Little's Law (`concurrency = throughput × latency`) for the connection/fleet estimate.
- Nuances: [[wiki/system-design-concepts/rate-limiting]] / [[wiki/theory/rate-limiting-algorithms]] — load-shedding/admission-control here is rate limiting applied to a scarce *compute* resource, not an API quota.
- Grounded by: [[wiki/theory/latency-numbers]] — why ms-scale message delivery and s-scale generation are different regimes.

## Concepts flagged for wiki (pending /update-wiki)
- Constrained-resource serving & GPU-fleet economics (queue vs. capacity vs. load-shed).
- LLM inference internals: KV cache, continuous batching, memory-bandwidth roofline.
- Streaming return path: session registry + pub/sub back-channel.
- Durable log vs. real-time channel separation.
- Admission control / graceful degradation under sustained overload.
