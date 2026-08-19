# Serving a Constrained, Expensive Resource

Some backends aren't cheap commodity boxes you can autoscale on demand — they're **scarce, expensive, and slow to provision** (GPUs for LLM inference, specialized hardware, licensed capacity). When the serving tier is one of these, the whole design inverts: it stops being about moving bytes and becomes about **protecting and scheduling the scarce resource**. This is the crux of "design ChatGPT," and it generalizes.

## The core insight: a queue smooths bursts, it cannot fix a deficit

The tempting move when requests outpace capacity is "add a durable queue (Kafka) and let requests wait." That is **only correct when arrival ≤ service *on average*** and the queue is absorbing *variance* (bursts) around a serviceable mean. 

If arrival **exceeds** service on average — e.g. 2M req/s arriving vs. a GPU fleet that serves far less — a queue just converts "reject requests" into "make every user wait an unbounded, ever-growing amount of time," which is strictly worse: the backlog grows to hours, every request times out, and you've spent durability writes to achieve it. **You cannot buffer your way out of a sustained capacity deficit.**

## The only three levers under sustained overload

1. **Add capacity** — provision the scarce resource closer to peak. Expensive and slow (you can't spin up GPUs like web servers), so it's a planning lever, not a real-time one.
2. **Shed load (admission control)** — reject at the door with a clear signal ("at capacity, retry later"), and/or enforce **per-user concurrency caps** and **priority tiers** (paid before free, interactive before batch). Bounds latency for admitted requests.
3. **Degrade quality** — serve a cheaper variant: a smaller/faster model, a shorter response, a cached/approximate answer. Keeps the product usable when the premium path is saturated.

Queueing is a *fourth, limited* lever that only handles short bursts. The reflex a strong candidate shows: on seeing arrival ≫ service, immediately reach for shed/degrade/capacity — **not** a bigger buffer.

## How it works

- Put **admission control at the edge** (before the expensive hop): token-bucket / concurrency limiter keyed by user or tier — this is [[wiki/system-design-concepts/rate-limiting]] applied to a *compute* resource rather than an API quota.
- A **scheduler** in front of the fleet packs work to maximize utilization (for GPUs, that means batching — see [[wiki/system-design-concepts/llm-inference-serving]]) while respecting priority/SLA.
- Emit **backpressure** upstream so the edge sheds instead of the fleet melting down.

## Key points
- Name the constrained resource explicitly and size it *quantitatively*; the arrival-vs-service ratio dictates which lever applies. A 1000× gap is a capacity/shed problem, full stop.
- "Highly durable queue" ≠ "handles peak." Durability protects against *loss*, not against *overload*.
- GPU capacity behaves like an **incompressible resource** — you can't overcommit it the way you can CPU ([[wiki/system-design-concepts/compressible-vs-incompressible-resources]]).
- Schedulers for scarce resources tend to **queue / scale / shed**, echoing [[wiki/system-design-concepts/preemption-economics]].

## Interview angle
*30-second answer:* "When the backend is a scarce, expensive resource like a GPU fleet, a queue only smooths bursts — it can't rescue a sustained arrival-vs-service deficit, because the backlog just grows without bound. My real levers are add capacity, shed load with admission control and priority tiers, or degrade to a cheaper model. So the design becomes: admission control at the edge, a batching-aware scheduler in front of the fleet, and backpressure between them."

## Connections
- [[wiki/system-design-concepts/llm-inference-serving]] — the concrete scarce resource (GPU) and how batching sets its capacity
- [[wiki/system-design-concepts/async-response-routing]] — decoupling the edge from the fleet forces a return-path design
- [[wiki/system-design-concepts/rate-limiting]] — admission control is rate limiting on a compute resource
- [[wiki/system-design-concepts/preemption-economics]] — scarce-resource schedulers queue/scale/kill rather than context-switch
- [[wiki/system-design-concepts/compressible-vs-incompressible-resources]] — GPU capacity can't be overcommitted like CPU
- [[wiki/system-design-concepts/work-distribution]] — distributing bounded work across a fleet

## Sources
- [[sources/docs/design-chatgpt-mock-interview]] — the "queue can't absorb a 1000× gap" correction and the three-levers framing
