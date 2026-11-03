# Client Identification (for rate limiting & abuse)

A [[system-design-concepts/rate-limiting|rate limiter]] is only as good as the identity it keys on, and *which* attribute is trustworthy hinges on one question: **is the caller authenticated?** The answer flips the whole problem — and determines whether tricks like VPN-hopping matter at all.

## Authenticated API — the credential *is* the identity
When the caller presents an **API key / OAuth token / account ID** (Twitter's API, any SaaS backend), key the limit on it directly. A VPN changes the IP but not the token, so **IP-hopping does nothing** to evade an API-key limit — which is why the [[system-design-concepts/global-rate-limiting|multi-region]] VPN-hopping worry mostly evaporates for authenticated surfaces. IP is only a *secondary anomaly signal*:
- one key from 10,000 IPs → credential sharing / theft;
- one IP minting 10,000 keys → an abuse ring.

## Unauthenticated surfaces — where IP matters, and betrays you
Login, signup, password reset, token issuance, anonymous reads: no account yet, so a network-derived key is the best coarse identity — and exactly where raw IP fails.

| Fundamental | Why raw IP fails / what to do |
|---|---|
| **CGNAT / NAT** | Carrier-grade NAT puts *thousands* of mobile users behind one public IPv4; an office/VPN exit is one IP for many humans → false positives (throttle innocents) *and* false negatives (one user across many IPs). |
| **ASN / BGP** | Every IP belongs to an **Autonomous System** (network operator). Distinguish *residential/eyeball* ASNs from *datacenter/hosting* (AWS, OVH) and known *VPN/Tor* egress. Consumer traffic from a datacenter ASN is inherently suspicious — limit/challenge it harder. Commercial VPN egress ranges are enumerable threat intel. |
| **Subnet aggregation & the IPv6 trap** | Limit per **/24** (IPv4) or per **/64** (IPv6), *not* per exact address. A single IPv6 customer gets a whole /64; limiting per /128 lets them rotate for free within their own allocation. The /64 is the "one customer" unit. |
| **Real client IP recovery** | The source IP you see is the last hop (LB/CDN), not the client. Reconstruct via `X-Forwarded-For` with a correct trusted-hop count, RFC 7239 `Forwarded`, or Proxy Protocol (L4). Wrong config → bucket everyone as the LB (limit collapses) or trust attacker-supplied XFF (limit evaporates). |

## Beyond IP — signals that survive IP rotation
A determined attacker rotates IPs (VPNs, residential-proxy networks), so layer in:
- **TLS fingerprinting (JA3 / JA4)** — the ClientHello (cipher suites, extensions, curves, *and their order*) fingerprints the client *software/stack*. A given bot/library has a characteristic JA4 hash *regardless of IP*, so 1,000 rotating IPs may share one telltale fingerprint. The most useful "who is this really" signal for bot detection.
- **TCP/IP stack fingerprinting (p0f-style)** — TTL, window size, TCP options reveal OS/stack; can expose that "1,000 IPs" are one toolchain.
- **HTTP/2 fingerprinting & header order**; for browsers, **device fingerprinting** (canvas/fonts) and **device IDs / cookies** for cross-IP stickiness (clearable → weak alone).

## The honest limit — identify → raise cost + score + degrade
You **cannot** perfectly identify the source of an unauthenticated request over the modern internet — NAT, VPNs, and residential proxies make it fundamentally ambiguous. So the goal shifts from *identify* to **raise attacker cost, score composite signals, and degrade gracefully**:
1. **Composite, multi-granularity identity** — rate-limit simultaneously per-account, per-IP, per-/64, per-ASN, and per-JA4, tripping on the *tightest* that matches. A hierarchy, not one key.
2. **ASN reputation as a multiplier** — same limit, but datacenter/VPN ASNs get a fraction of the residential budget.
3. **Degrade, don't hard-block, on weak signals** — escalate to a **challenge** (CAPTCHA, proof-of-work, step-up auth) rather than a 429, so a shared-IP false positive costs a real user a challenge, not an outage.

## Key points
- Authenticated API → key on the credential; VPN/geo-hopping is irrelevant. IP is only a secondary anomaly signal.
- Unauthenticated → IP is the coarse key, but CGNAT, ASN type, and IPv6 /64 allocation make raw IP unreliable both ways.
- Aggregate to /24 (v4) or /64 (v6); never per /128. Recover the real client IP via trusted-hop config (the XFF trap cuts both ways).
- IP is defeatable → layer JA3/JA4 TLS fingerprints and stack fingerprints that survive IP rotation.
- Perfect source identification is impossible; use composite scoring + ASN reputation + degrade-to-challenge.

## Interview angle

> "First question: is the caller authenticated? If they present an API key or token, that credential *is* the identity — I key on it and VPN-hopping does nothing, since a VPN changes the IP but not the token. IP is only a secondary signal for anomaly detection. For unauthenticated surfaces like login, IP is the coarse key but it lies: CGNAT hides thousands of users behind one address, and an IPv6 user gets a whole /64, so I aggregate to /24 or /64, never per exact address, and I weight datacenter and VPN ASNs harder than residential. Because IPs rotate, I add JA3/JA4 TLS fingerprints that identify the client stack regardless of IP. The honest part: you can't perfectly identify an anonymous client, so I score composite signals and degrade to a challenge rather than pretend a hard block is accurate."

## Connections
- [[system-design-concepts/rate-limiting]] — identity is the key the limiter counts on; "identity is composed, not intrinsic"
- [[tech/envoy-ratelimit-service]] — the descriptor/action model: at the edge you harden a spoofable IP/header, in-mesh you key on a verified SPIFFE principal
- [[system-design-concepts/global-rate-limiting]] — why an authenticated API key makes VPN-hopping across regions a non-issue
- [[theory/osi-model]] — where these signals live: IP/ASN at L3, TCP fingerprint at L4, TLS/JA4 at the TLS layer, headers at L7
- [[tech/vpn]] — how VPNs relocate the source IP, which is exactly what defeats naive IP-based limiting
- [[system-design-concepts/zero-trust-ztna]] — the same "verified identity over network location" principle

## Sources
- [[sources/docs/rate-limiting-study-guide]] — §5.2b identifying the external client (authenticated vs anonymous, beyond-IP signals)
- [rate-limiting-study-guide.html](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/rate-limiting-study-guide.html#rls-identity2) — CGNAT/ASN//64/XFF table, JA3/JA4 fingerprinting, composite identity, degrade-to-challenge
