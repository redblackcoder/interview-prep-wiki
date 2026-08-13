# Istio Service Mesh

Istio's data plane **is Envoy**: a sidecar proxy injected next to each app, carrying all its traffic. The mental model that makes everything click: **you never write Envoy config by hand — you write Kubernetes objects and Istio CRDs, and a control-plane component (`istiod`) compiles them into Envoy config and pushes it to every proxy.** Your YAML is the *source*; the running Envoys are the *output*.

## The building blocks
- **Pod / Deployment / Service** — standard Kubernetes: a Deployment keeps N pod copies; a Service is a stable name + virtual IP fronting them. A plain **ClusterIP** Service has no external address — that's "internal" part 1.
- **Sidecar (Envoy)** — auto-injected (via a namespace label `istio-injection: enabled`) next to your app. mTLS, routing, and rate-limit checks happen here; your app is unaware. A Deployment with *one* container becomes a pod with *two* (app + `istio-proxy`).
- **istiod** — the control plane. Watches CRDs + cluster state, compiles Envoy config, pushes it via xDS, and issues each workload its **mTLS identity certificate**.
- **CRDs** — `Gateway`, `VirtualService`, `PeerAuthentication`, `AuthorizationPolicy`, `EnvoyFilter`: declarative *rules* istiod turns into Envoy config; they configure proxies, they don't run.

## Traffic management: Gateway vs VirtualService
The split that confuses everyone: **Gateway** = "open this port/protocol/host at the edge" (the *door*); **VirtualService** = "once inside, route path X to service Y" (the *signposting*). The `istio-ingressgateway` is a pre-existing Envoy deployment; `selector: istio: ingressgateway` attaches your door to it. Neither is a server you deploy — both are config for one that already exists.

## Security: mTLS + SPIFFE + AuthorizationPolicy
- **`PeerAuthentication` STRICT** — every pod-to-pod call between sidecars must be mutually-TLS authenticated. This is what gives authorization something to check: each call now carries a verified caller identity.
- **SPIFFE identity** — each workload's ServiceAccount mints an mTLS cert with a SPIFFE name (`spiffe://cluster.local/ns/demo/sa/service-a`). Cryptographically verified, **not spoofable**.
- **`AuthorizationPolicy`** — B's sidecar checks the caller's *verified mTLS principal* against an allow-list; anything that isn't Service A gets **403 inside the cluster**. This is "internal only" part 2: unreachable from outside (no Gateway) *and* locked down inside (identity check). The concrete realization of "verified service identity, not IP" (see [[system-design-concepts/network-security-layers]]).

## EnvoyFilter — the escape hatch
Istio has no built-in "global rate limit" CRD, so you patch raw Envoy config with `EnvoyFilter` to (a) insert the `http.ratelimit` filter into the gateway's chain and (b) define a cluster pointing at the [[tech/envoy-ratelimit-service|RLS]]. It's a sharp tool: lives only at `networking.istio.io/v1alpha3`, patches raw config, and a bad match can break a proxy — use it only where Istio lacks a higher-level API.

## First-timer gotchas
- **domain mismatch = silent no-op** — the `domain` in the RL filter must equal the RLS ConfigMap's `domain`, or the RLS has no config for the request and returns OK for everything. Rate limiting fails *open*, so the mistake hides. (Descriptor key/value mismatches fail the same silent way.)
- **Keep RLS & Redis out of the mesh** (in a simple setup) — a hand-rolled plaintext gRPC cluster to the RLS is rejected if the RLS has a STRICT-mTLS sidecar; `sidecar.istio.io/inject: "false"` avoids it (production alternative: inject them and reference Istio's auto-generated `outbound|...` cluster so mTLS is handled).
- **Port names are semantic** — Istio infers L7 protocol from the Service port *name* (`http`, `grpc`, `tcp-redis`); misname an HTTP port and it's treated as opaque TCP, so routing/rate-limiting won't apply.

## One request, end-to-end (edge RL + internal authz)
Client → cloud LB → `istio-ingressgateway` → matches Gateway listener + VirtualService route → **RL filter** builds a descriptor and gRPC-calls the RLS → RLS does `INCRBY`+`EXPIRE` in Redis → OVER_LIMIT ⇒ gateway returns 429 (upstream never touched); OK ⇒ forwards to Service A's pod → A's sidecar terminates mTLS → A calls `service-b` → A's sidecar opens mTLS to B's sidecar with A's SPIFFE identity → B's sidecar checks AuthorizationPolicy (principal = `.../sa/service-a` → allowed) → response flows back.

## Key points
- You configure Envoys indirectly through CRDs; istiod compiles + pushes via xDS.
- Gateway = the door (edge listener); VirtualService = the routing once inside.
- mTLS (PeerAuthentication) + SPIFFE + AuthorizationPolicy give unspoofable, identity-based east-west access control.
- EnvoyFilter is the raw-config escape hatch for features without a CRD (e.g. global rate limiting) — powerful and dangerous.
- Rate limiting fails open, so config mismatches are silent — the #1 "why isn't it working."

## Interview angle

> "Istio's data plane is Envoy sidecars; you never hand-write proxy config — you write Kubernetes objects and Istio CRDs, and istiod compiles and pushes them via xDS. Gateway opens the edge door, VirtualService routes once inside. Security is mTLS everywhere plus a verified SPIFFE identity per workload, so an AuthorizationPolicy can allow only a specific service account — real identity-based access control, not IP. For global rate limiting there's no CRD, so you drop to an EnvoyFilter to wire the ratelimit filter and a cluster to the RLS — and the classic trap is that a domain or descriptor mismatch fails open silently, because a limiter should never take down the app."

## Connections
- [[tech/envoy-ratelimit-service]] — the RLS that EnvoyFilter wires onto the gateway
- [[system-design-concepts/rate-limiting]] — edge (global, L7) enforcement is one cell of the local-vs-global grid
- [[system-design-concepts/network-security-layers]] — mTLS/SPIFFE authz is the "verified identity, not IP" fix for the flat-network attack; ZTNA per-request authorization
- [[system-design-concepts/zero-trust-ztna]] — a mesh with per-call mTLS authz is Zero Trust applied east-west
- [[tech/https-tls]] — the mesh's mTLS reuses the same TLS confidentiality/integrity/auth machinery, mutually

## Sources
- [[sources/docs/istio-rls-deployment-walkthrough]] — the complete K8s + Istio manifests (namespace → services → gateway → mTLS → authz → Redis → RLS → EnvoyFilters) and the who-configures-what map
- [istio-rls-deployment-walkthrough.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/istio-rls-deployment-walkthrough.html) — annotated deployment with topology diagram and end-to-end request flow
