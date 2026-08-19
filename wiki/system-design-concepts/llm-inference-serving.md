# LLM Inference Serving: KV Cache & Continuous Batching

The GPU tier is the crux of any "design ChatGPT / an LLM product" question, and the failure mode is treating it as a black box. You don't need CUDA, but you need a working model of **what sets a GPU's serving capacity** — because that number drives fleet sizing, cost, and every scheduling decision above it.

## Why generation is different from a normal request
- A single generation **holds the GPU for seconds**, streaming **one token at a time** — the connection stays open the whole time.
- **Decode is memory-bandwidth-bound, not compute-bound.** Generating each token re-reads the model weights and the growing attention state from GPU memory; you're waiting on memory bandwidth, not FLOPs. This is why throughput comes from *doing more in parallel*, not from a faster clock (see the roofline framing in [[wiki/theory/latency-numbers]] — latency stalls while bandwidth grows).

## KV cache — the thing that bounds concurrency
Attention needs the keys/values of every prior token. Recomputing them each step is quadratic, so they're cached: the **KV cache**. Its size grows with **context length × layers × heads × head-dim × 2 × dtype-bytes**, *per in-flight sequence*.

- The KV cache, not compute, usually **caps how many sequences fit on one GPU** — it's the memory budget you're actually fighting.
- Longer conversations = larger KV cache = fewer concurrent sequences = lower throughput. This is the hidden cost of multi-turn context: every turn re-sends and re-attends over the growing history.
- Mitigations that show up in real systems: multi-query / grouped-query attention (shrink K/V heads), quantization, and **PagedAttention** (vLLM) — treat the KV cache like OS paged virtual memory to cut fragmentation and pack more sequences.

## Continuous (in-flight) batching — the thing that creates capacity
Because decode is memory-bound, running one sequence wastes the GPU. **Batching** many sequences through the model together amortizes the weight reads across all of them — near-linear throughput gains until the KV-cache memory budget is exhausted.

- **Static batching** waits to assemble a batch and releases it together — a fast request is held hostage by the slowest in its batch.
- **Continuous batching** admits and evicts sequences *mid-flight*: as one sequence finishes, a queued one takes its slot on the next step. This is the ~10–20× throughput technique behind modern serving engines (vLLM, TGI, TensorRT-LLM).

## Key points
- Capacity ≈ *how many sequences fit in KV-cache memory* × *how efficiently the batcher keeps them packed*. That product is your per-GPU QPS; multiply by fleet size for total.
- Throughput and latency trade off: bigger batches = more throughput but higher per-token latency. SLA-aware batching is the balancing act.
- This is what makes the GPU an [[wiki/system-design-concepts/serving-constrained-resources|expensive, constrained resource]]: you can't cheaply overprovision, so batching efficiency is money.

## Interview angle
*30-second answer:* "A GPU's serving capacity is set by two things: the KV cache — attention state per sequence that grows with context length and eats GPU memory, usually capping concurrency before compute does — and continuous batching, which packs many sequences through the memory-bound decode step so the GPU isn't idle. Capacity is roughly how many sequences fit in KV-cache memory times how well the batcher keeps them full, and that's the number I'd size the fleet and cost model on."

## Connections
- [[wiki/system-design-concepts/serving-constrained-resources]] — batching efficiency is what makes the scarce GPU economical; capacity feeds the shed/degrade decision
- [[wiki/system-design-concepts/async-response-routing]] — the token-by-token stream this produces has to be routed back to the caller
- [[wiki/theory/latency-numbers]] — memory-bandwidth-bound decode; the latency-vs-bandwidth divergence

## Sources
- [[sources/docs/design-chatgpt-mock-interview]] — flagged as the unopened black box that capped the round
- External reading (see [[private/staff-swe-readiness-report|action plan]]): kipply "Transformer Inference Arithmetic" (KV-cache sizing math), Anyscale "How Continuous Batching Enables 23x Throughput," vLLM/PagedAttention (SOSP 2023)
