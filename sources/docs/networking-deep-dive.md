---
source: docs/networking-deep-dive/networking-study-guide.html
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/networking-deep-dive/networking-study-guide.html
type: doc
date_extracted: 2026-07-26
topic: tech / system-design-concepts
---

# Networking Deep Dive — OSI, HTTPS & VPN

A single-artifact study guide that arcs from the OSI model through the full HTTPS/TLS
handshake, argues why HTTPS alone is insufficient, then builds a VPN layer-by-layer over
the public internet. The through-line: **every security property has to be placed at a
specific layer** — HTTPS secures the *payload* at L5–7; a VPN secures the *envelope* at L3.

## Key Ideas

### OSI & encapsulation
- **7 layers**, PDUs top→bottom: Data (7 App / 6 Presentation / 5 Session) → **Segment** (4 Transport) → **Packet** (3 Network) → **Frame** (2 Data Link) → **Bits** (1 Physical).
- **Encapsulation**: sending host wraps data going *down* (each layer prepends its header; L2 also appends a trailer/FCS), receiving host strips going *up*. A layer only reads the header its *peer* layer wrote — this is what lets you swap Wi-Fi for Ethernet without touching the app.
- Real stack is **TCP/IP's 4 layers** (App / Transport / Internet / Link); OSI is the teaching model.
- **Where TLS lives**: functionally L5/6 (session + presentation), running over TCP (L4). QUIC folds TLS 1.3 into an L4 transport over UDP.
- The pivotal fact for everything downstream: **the IP + TCP headers (src/dst IP, port, size, timing) sit *outside* the TLS-encrypted envelope** and are visible to anyone on-path.

### HTTPS = HTTP over TLS
- TLS delivers four properties: **confidentiality** (symmetric AEAD — AES-GCM / ChaCha20-Poly1305), **integrity** (AEAD auth tag), **authentication** (X.509 cert signed by a trusted CA), **forward secrecy** (ephemeral ECDHE — a later-stolen server key can't decrypt captured traffic).
- Setup order: DNS (plaintext, leaks hostname) → TCP 3-way handshake (SYN / SYN-ACK / ACK) → TLS handshake.
- **TLS 1.2 = 2 RTT**: ClientHello (ciphers, client random, SNI) → ServerHello + Certificate + ServerKeyExchange (signed ephemeral key) → ClientKeyExchange → ChangeCipherSpec + Finished. Both sides derive the same master secret from the DH exchange; the cert key only *signs*, never encrypts the secret.
- **TLS 1.3 = 1 RTT** (0-RTT on resume): client sends `key_share` in the first flight (guesses the curve); everything after ServerHello — including the certificate — is encrypted; all legacy/weak crypto (RC4, CBC MAC, static-RSA key exchange, compression, renegotiation) removed; forward secrecy mandatory.
- **Cipher suite anatomy** (TLS 1.2): `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` = key-exchange `ECDHE` + auth `RSA` + bulk cipher `AES-128-GCM` + hash `SHA256`. TLS 1.3 keeps only the symmetric half (`TLS_AES_256_GCM_SHA384`).
- **PKI chain of trust**: browser trust store (~150 root CAs) → root signs intermediate → intermediate signs leaf. Browser verifies signature chain, hostname∈SAN, validity window, revocation (OCSP/CRL), and proof-of-possession (server signs with the cert's private key). Trust model's weakness: *any* of ~150 CAs can mint a valid cert for your domain (DigiNotar 2011) → mitigated by Certificate Transparency, CAA, HSTS, pinning.
- **Wire order** (outer→in): Ethernet frame → IPv4 packet → TCP segment → TLS record (5-byte header + AEAD ciphertext) → HTTP.

### Why HTTPS is not sufficient
- **It secures one app session's payload — not the network.** Six gaps: (1) **metadata/envelope** — IP/TCP headers plaintext, so on-path parties see who↔who, when, how often, how much; (2) **hostname leakage** — plaintext DNS + TLS SNI reveal the host before encryption (DoH/DoT/ECH only partially deployed); (3) **no network access control** — TLS authenticates the server *to the client*, does nothing to stop the whole internet *reaching* an internal service; (4) **fragile CA trust**; (5) **per-service burden** — every service must implement/patch TLS+authz itself; miss one and it's exposed; (6) **endpoint/app** — TLS doesn't stop a phished credential, XSS, or a compromised host.
- **Reframing**: HTTPS answers *"is this conversation private and is the server who it claims?"* It never answers *"should this person be allowed to reach this server at all?"* — a **network** (L3) question, which is a VPN's job.
- **Concrete attack** (flat network, publicly-exposed internal Kibana with valid TLS): ① mass-scan IPv4:443 finds it → ② TLS cert SAN + HTTP title leak software version → ③ exploit an app-layer CVE *inside* valid TLS → ④ shell on a box sitting in a flat LAN → ⑤ lateral pivot to DB / secrets / neighbors → ⑥ exfil over :443, looks like normal web traffic. Every arrow was encrypted; padlock green throughout. **Root cause: the service was reachable by everyone.** Fix isn't more encryption — it's removing it from the public internet behind an authenticated gateway.

### Building a VPN, layer by layer
Each step defeats a specific threat:
1. **Tunneling / encapsulation** — internal servers use RFC 1918 private IPs (`10.0.0.0/8`) that aren't internet-routable, so wrap the client's original inner packet (dst `10.x`) as *payload* inside an outer packet addressed to the gateway's public IP. **A packet within a packet** — the internet only sees client→gateway.
2. **Encryption (confidentiality)** — encrypt the *entire* inner packet before encapsulating, so even the inner IP header (real src/dst/port) is hidden. This closes the metadata leak HTTPS left open.
3. **Authentication & integrity** — AEAD tag / HMAC per packet (tamper → drop); peer authentication (PSK / cert / pubkey) proves enrolled device; anti-replay sliding window. Same triad as TLS but applied to *whole L3 packets, every protocol*.
4. **Key exchange** — authenticated ephemeral (EC)DH → forward secrecy + periodic rekey. IPsec calls it **IKE**; WireGuard uses a fixed **Noise** handshake.
5. **Virtual interface (TUN/TAP)** — a TUN device (`utun0`/`wg0`, L3 IP packets) makes the OS transparently route traffic into the encrypt-encapsulate engine; TAP = L2 frames. Client gets a VPN-network IP (e.g. `10.8.0.2`).
6. **Routing — split vs full tunnel** — split: only corporate subnets go through VPN (fast, but endpoint straddles two nets); full (`0.0.0.0/0` → tunnel): all traffic egresses via gateway (uniform monitoring, hides client IP — the "route public traffic through corporate too" requirement).
7. **Gateway / DMZ / segmentation** — the concentrator is the *only* public-facing piece, in a DMZ behind the edge firewall (only the VPN port open inbound). After decryption it does *not* grant flat access: firewall ACLs limit reachable subnets/ports (kills lateral movement), microsegmentation, NAT for return path, and pushes internal DNS resolvers.

- **Protocols implement the same 7 steps differently**: IPsec ESP = steps 1–3, IKE = step 4 (site-to-site, native clients); OpenVPN reuses TLS as its step-3/4 engine (can hide on :443, traverses firewalls); WireGuard hard-codes a fixed modern suite into ~4k LOC (fast, auditable, single UDP port).
- **Packet walkthrough (defense in depth)**: opening `https://wiki.corp.internal` yields **two independent encryption layers** — inner TLS (§2, true end-to-end; even the gateway can't read the HTTP body) *inside* the outer VPN wrap (network admission + metadata privacy). Re-running the §3 attack: the internet scan now finds only the gateway's one hardened UDP port; `10.1.5.4` doesn't exist to the attacker → no admission → no app to exploit → no lateral movement.

### Beyond VPN — Zero Trust (ZTNA)
- Classic VPN is **perimeter-based**: once on the tunnel you're "inside," and only segmentation limits you; a compromised laptop inherits broad access.
- **ZTNA** ("never trust, always verify"): grants access to *specific applications* not network subnets; verifies identity + device posture + context on *every request*; apps hidden behind an identity-aware proxy/broker (no known public endpoint); lateral movement denied by default. Same layered discipline (encapsulate/encrypt/authenticate/authorize) but the authz decision shifts from *"which network are you on"* → *"who are you, on what device, requesting which app, right now."*

## My Understanding
- The whole guide is one idea repeated at two altitudes: **encrypt + authenticate the thing, but decide separately who's allowed to reach it.** HTTPS nails the first at the payload layer and completely skips the second. A VPN adds the second at L3 by making internal services unaddressable from outside and putting a single authenticated door in front.
- The "packet within a packet" reframing is the unlock. Once I see the VPN wrap as *just another encapsulation layer below IP*, the fact that it hides the inner IP header (the metadata TLS leaks) becomes obvious rather than magic — it's payload now.
- Defense-in-depth clicks best from the packet walkthrough: inner TLS and outer VPN aren't redundant, they answer different questions (end-to-end app secrecy vs network admission), which is why serious setups run both and why ZTNA is the logical end state — push the "who/what/which-app" check down to *per request* instead of *once at tunnel connect*.

## Open Questions
- Encrypted ClientHello (ECH) + DoH deployment reality — how much of the SNI/DNS hostname leak is actually closed in practice today vs still observable?
- WireGuard's fixed-crypto stance vs IPsec's negotiability — what's the real operational cost of "no agility" when a primitive needs rotating?
- Where exactly does the VPN gateway's segmentation ACL live vs a service mesh's mTLS authz in a ZTNA world — when do you still need the L3 tunnel at all?

## Connections
- Seeds: [[tech/vpn]] — the centerpiece (layer-by-layer build, protocols, packet walkthrough)
- Seeds: [[tech/https-tls]] — HTTPS/TLS handshake, PKI, forward secrecy
- Seeds: [[theory/osi-model]] — the layered foundation every other page references
- Seeds: [[system-design-concepts/network-security-layers]] — payload-vs-envelope principle + the attack scenario
- Seeds: [[system-design-concepts/zero-trust-ztna]] — the evolution beyond perimeter VPNs
- Relates to: [[system-design-concepts/work-distribution]] — a VPN gateway is itself a scaled, load-balanced fleet with the same admission concerns

## Key Quotes / Annotations
The one sentence for the metadata gap:
> Everything from the TCP header outward — your IP, the server's IP, the port, packet sizes and timing — is plaintext metadata. TLS encrypts only the innermost payload.

The reframing that motivates the VPN:
> HTTPS answers "is this conversation private and is the server who it claims to be?" It never answers "should this person even be allowed to reach this server at all?" That second question is a network question — and it belongs at Layer 3, which is exactly where a VPN operates.

Attack root cause:
> The server was reachable by everyone. HTTPS secured the pipe but never asked "who is allowed to open a pipe at all?" The fix isn't more encryption — it's removing the service from the public internet behind an authenticated network gateway.

Defense in depth (the payoff):
> Two independent encryption layers. The inner TLS gives true end-to-end app security (even the VPN gateway can't read the HTTP body). The outer VPN gives network-level access control + metadata privacy + authenticated admission.
