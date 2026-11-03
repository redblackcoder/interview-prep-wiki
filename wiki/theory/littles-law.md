# Little's Law

For any stable system: **L = λ × W** — average items in the system = arrival rate × average time each spends in it. No assumptions about distributions. It's the single most useful capacity identity in system design, because it converts between the three numbers interviewers give you (rate, duration, concurrency).

## The one-sentence mental model

> **Concurrency = rate × duration. If you know two, you know the third — and if your numbers violate it, your design is wrong before it starts.**

## Worked examples
- **Job scheduler:** 10k starts/s × 600 s avg run = **6M running**. Heartbeats @ 30 s = 6M / 30 = **200k writes/s**. (Common slip: "10k running ⇒ 10k starting per second" — that implies 1 s jobs.)
- **Ride dispatch:** 20k offers/s × 30 s timeout = **600k outstanding offers**.
- **Web server:** 5k QPS × 200 ms latency = **1,000 concurrent requests** ⇒ thread pool / connection sizing.
- **Queue:** if arrivals λ exceed service rate μ, L grows without bound — no buffer fixes a sustained λ > μ; you shed, degrade, or add capacity.

## Key points
- Run it in **both directions** as a sanity check on any requirement: "10k concurrent with 10-min jobs" means only ~17 starts/s — a very different problem from 10k starts/s.
- Periodic per-item work (heartbeats, polling) scales with **L**, not λ — that's usually where the hidden load is. Batching per-item rows cuts transactions, not row writes; to divide the *writes* by items-per-host, make the work per-host (one lease per driver). Compare at the same interval: 6M / 30 s = 200k vs 6,000 drivers / 30 s = 200.
- **Different cohorts, same second.** 4 writes per job × 10k/s = 40k writes/s works because in steady state 10k jobs enter and 10k leave each second — the insert, claim, start, and finish in one second belong to four different sets of jobs.
- Holds per subsystem too: apply it to each queue/stage to find where items pile up.

## Interview angle

> "Before sizing, I convert: 10k starts a second times a 10-minute average is six million running. That's what drives the design — any per-running-job write, like a heartbeat, is six million over its interval. At 30 seconds that's 200k writes a second, so the lease is per driver, not per job — 200 a second."

## Connections
- [[theory/latency-numbers]] — the other estimation primitives
- [[system-design-concepts/distributed-job-scheduler]] — heartbeat load derived from L
- [[system-design-concepts/serving-constrained-resources]] — λ > μ can't be buffered
- [[system-design-concepts/lease-and-fencing]] — per-lease renewals scale with L; batch them

## Sources
- [[sources/docs/design-job-scheduler-mock-interview]] — Little's law for running jobs and heartbeat writes
- [[sources/docs/design-uber-driver-allocation-mock-interview]] — 600k outstanding offers
