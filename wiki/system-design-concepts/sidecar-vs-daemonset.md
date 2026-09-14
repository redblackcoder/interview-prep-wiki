# Sidecar vs DaemonSet (Agent Deployment Topology)

Where does a node-local helper — a metrics/log agent, a proxy, a collector — actually run? Two Kubernetes topologies: a **sidecar** (an extra container *in every application pod*, one per pod, sharing the pod's lifecycle and network namespace) or a **DaemonSet** (exactly *one pod per node*, independent lifecycle, shared by every app pod on that node). The naive comparison is "runtime memory," and that's the least interesting axis. The decision is really made by **upgradeability** and **blast radius**.

## The one-sentence mental model

> **Sidecar = per-workload, isolated, but version-pinned to the app and multiplied by pod count. DaemonSet = one-per-node, centrally upgradable, cheaper at density — but a shared dependency that mixes tenants. For a fleet-wide shipping agent, DaemonSet usually wins; for per-workload isolation or app-specific config, sidecar wins.**

## The four axes that actually decide it

1. **Upgradeability / ownership (usually the deciding factor).** A sidecar's version is **pinned to the app's deployment** — shipping an agent fix means redeploying every service (or relying on an admission-webhook re-injection), and teams will pin stale versions. A DaemonSet is upgraded **centrally by the platform team, decoupled from app deploys.** This is why real telemetry agents — Datadog agent, otel-collector in agent mode, fluent-bit, node_exporter — are almost always DaemonSets.
2. **Blast radius / multi-tenancy (the cost of DaemonSet).** A DaemonSet is a **shared dependency**: one misbehaving pod flooding it degrades metrics for *every* pod on the node, and it now mixes tenants — so you need per-source rate limiting and fair queuing, plus source attribution. A sidecar is **naturally isolated** per workload; a bug or flood is contained to its pod.
3. **Resource cost at density.** N pods on a node ⇒ **N sidecars**, each paying a baseline footprint (e.g. 64 MB idle) even when doing nothing ⇒ N×64 MB. A DaemonSet is **one** instance regardless of pod count. At high pod density this is a large, mostly-idle overhead for sidecars. (This is the axis interviewers surface first; it's real but rarely the *deciding* one.)
4. **Node-level aggregation.** A DaemonSet sees **every pod on the node**, so it can fold across them before shipping — a second fan-in reduction on top of per-process pre-aggregation. A sidecar only sees its own pod's stream.

## Networking follows from the choice
- **Sidecar:** same pod, so `localhost`/loopback, or a **Unix domain socket over a shared `emptyDir` volume**. Cheapest and most private.
- **DaemonSet:** the app must reach the node agent — either a **`hostPort` + the node IP** (via the downward API), or a **UDS on a `hostPath`** bind-mounted into every pod (the otel/Datadog pattern).
See [[system-design-concepts/local-ipc-transports]] for the transport-semantics choice on that hop.

## Lifecycle nuance
Historically a sidecar's shared lifecycle caused ordering pain (agent must start before / stop after the app; a dead agent could wedge the pod). Kubernetes **native sidecars** (init containers with `restartPolicy: Always`, GA in 1.29) fix ordering and independent restart. A DaemonSet is lifecycle-independent by construction, but the app must **tolerate the agent being absent** — which fits a fire-and-forget, drop-on-full ingest path anyway.

## Key points
- **Decide on upgradeability + blast radius first, resource cost second.** "It saves memory" is the weakest correct reason; "I can ship an agent CVE fix without redeploying 400 services" is the strong one.
- **The pull toward DaemonSet is operability; the pull toward sidecar is isolation.** Name both.
- **DaemonSet ⇒ you owe per-source fairness** (rate-limit / attribute each pod) because it's a shared multi-tenant process.
- **DaemonSet enables node-level pre-aggregation**; sidecar cannot see peer pods.
- **Not a k8s-only distinction:** "one shared local daemon vs one embedded-per-process helper" is the same trade for host agents generally (systemd unit vs in-process library).
- Service meshes (Envoy) deliberately choose **sidecar** — they need per-workload identity, mTLS, and traffic isolation, which a shared node proxy can't give cleanly.

## Interview angle

> "The reflex answer is 'DaemonSet saves memory' — one per node instead of one per pod — and that's true at density, but it's not why I'd choose it. The real reasons are operational: a sidecar's version is pinned to the app's deploy, so shipping an agent fix means redeploying every service; a DaemonSet the platform team upgrades centrally. The price is that a DaemonSet is a shared dependency — one noisy pod can starve everyone on the node — so I'd add per-source rate limiting and attribution. I'd pick sidecar only when I need strong per-workload isolation or app-specific agent config, which is exactly why meshes use sidecars. For a fleet-wide counter agent, DaemonSet: central upgrades, node-level aggregation, one instance per node. Networking then is a hostPath Unix socket or the node IP, versus a shared-volume socket for a sidecar."

## Connections
- [[system-design-concepts/local-ipc-transports]] — how the app talks to the agent once you've placed it (UDS over emptyDir/hostPath, or UDP over the node IP)
- [[system-design-concepts/edge-shed-vs-core-durability]] — the agent is the edge; its fire-and-forget, tolerate-absence behavior is the "shed" end
- [[system-design-concepts/compressible-vs-incompressible-resources]] — sizing the agent's requests/limits (memory hard-reserved) is why N idle sidecars actually cost
- [[system-design-concepts/metrics-pull-vs-push]] — a node DaemonSet is the standard place a scrape target or push-forwarder lives
- [[system-design-concepts/work-distribution]] — one-per-node is a partitioning-by-node decision; per-source fairness is the anti-starvation requirement it creates

## Sources
- [[sources/docs/design-metrics-counters-mock-interview]] — the sidecar-vs-daemonset exchange; got resource-multiplication with a nudge, missed upgradeability + blast-radius
