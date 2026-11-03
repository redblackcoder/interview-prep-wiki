# VPN (Virtual Private Network)

A **VPN** creates a *virtual* private link across the shared, untrusted public internet, so a remote client behaves as if it were physically plugged into the corporate LAN. It exists to fix the gap that [[tech/https-tls|HTTPS]] leaves open (see [[system-design-concepts/network-security-layers]]): HTTPS secures a single app session's *payload*, but does nothing about *who is allowed to reach a server at all*. A VPN answers that network-level question at [[theory/osi-model|OSI]] L3 by (a) making internal services unaddressable from the internet and (b) forcing every packet through one authenticated, encrypted gateway.

## What we want
- A remote laptop reaches **private internal servers** (`10.0.0.0/8`) that have **no public IP**.
- Every packet — headers included — **encrypted & authenticated** across ISPs.
- Only **authenticated, authorized devices** get onto the network.
- Optionally, the client's **public internet** traffic also egresses through the corporate gateway (full tunnel).

## How it works — building it layer by layer

Each step defeats a specific threat.

**1 · Tunneling / encapsulation** — internal servers use RFC 1918 private IPs (`10.1.2.3`) that no internet router will forward. So wrap the client's original *inner* packet (dst `10.1.2.3`) as the **payload** of a new *outer* packet addressed to the gateway's **public** IP. A packet within a packet — the internet only ever sees `client → gateway`.
```
[ OUTER IP  src=client-public → dst=gateway-public
   [ VPN/tunnel hdr (ESP / WireGuard)
      [ INNER IP  src=10.8.0.2 → dst=10.1.2.3
         [ TCP | TLS | HTTP … ] ] ] ]
```

**2 · Encryption (confidentiality)** — encrypt the *entire* inner packet before encapsulating (AES-256-GCM / ChaCha20-Poly1305). Now even the **inner IP header** (real src/dst/port) is ciphertext. This directly closes the metadata leak HTTPS left exposed — an observer sees only `client → gateway`.

**3 · Authentication & integrity** — encryption alone doesn't prove *who* sent a packet. Add: AEAD tag / HMAC per packet (tamper → drop); **peer authentication** (pre-shared key, certificate, or public key) proving an enrolled device; **anti-replay** sliding window rejecting captured-and-resent packets. Same triad as TLS, but applied to whole L3 packets for every protocol.

**4 · Key exchange** — an authenticated ephemeral (EC)DH exchange produces the symmetric keys → **forward secrecy** + periodic rekey. IPsec calls this **IKE**; WireGuard uses a fixed **Noise**-protocol handshake. The peers' long-term identity keys authenticate the DH exchange, binding key material to a specific device.

**5 · Virtual interface (TUN/TAP)** — for apps to use the tunnel transparently, the OS needs an interface that *looks* real but pipes packets into the encrypt-encapsulate engine. A **TUN** device (`utun0`/`wg0`) works at L3 (IP packets); **TAP** works at L2 (Ethernet frames). TUN is the norm for remote access. The client gets a VPN-network IP (e.g. `10.8.0.2`).
```
app → kernel routing → utun0 → VPN engine (encrypt+encapsulate)
    → real NIC → internet → gateway (decap+decrypt) → internal LAN
```

**6 · Routing — split vs full tunnel** — the gateway pushes routes controlling what enters the tunnel:
- **Split tunnel**: only corporate subnets (`10.0.0.0/8 → utun0`) go through the VPN; personal traffic goes direct. Faster, less gateway load — but the endpoint straddles two networks (a risk).
- **Full tunnel**: *all* traffic (`0.0.0.0/0 → utun0`) egresses via the gateway. Enables uniform monitoring/filtering and hides the client's real IP — this is the "route public traffic through corporate too" mode.

**7 · Gateway / DMZ / segmentation** — the **VPN gateway (concentrator)** is the only public-facing piece. It sits in a **DMZ** behind the edge firewall, which permits *only* the VPN port inbound (e.g. UDP/51820) and drops everything else. Critically, after decryption it does **not** grant flat access: firewall/ACLs restrict which subnets/ports a VPN client may reach (kills lateral movement), microsegmentation isolates pools, NAT routes replies back to the client's VPN IP, and the gateway pushes internal DNS resolvers (so `*.corp.internal` resolves and internal DNS never leaks).

## The real protocols

Every protocol implements the same seven steps — they differ in *how*.

| | IPsec (IKEv2) | OpenVPN | WireGuard |
|---|---|---|---|
| Layer | L3 (kernel) | L3/L4 (userspace over TLS) | L3 (kernel) |
| Transport | ESP (proto 50) + IKE/UDP 500/4500 | UDP or TCP (often 1194/443) | UDP (single port) |
| Key exchange | IKEv2 (DH) | TLS handshake | Noise_IK (Curve25519) |
| Crypto | Negotiable | Negotiable (OpenSSL) | **Fixed** modern suite |
| Codebase | Large | ~100k LOC | **~4k LOC** (auditable) |
| Best for | Site-to-site, native clients | Firewall traversal (looks like HTTPS on :443) | Speed, simplicity, modern default |

Mapping back to the build: IPsec's **ESP** = steps 1–3, its **IKE** = step 4. OpenVPN reuses the [[tech/https-tls|TLS]] machinery as its step-3/4 engine (which is why it can hide on port 443). WireGuard hard-codes steps 2–4 into a tiny fixed handshake.

## End-to-end packet walkthrough (defense in depth)

Opening `https://wiki.corp.internal` from the remote laptop — note HTTPS and the VPN operate *simultaneously at different layers*:
```
① App builds  GET / HTTP/2  Host: wiki.corp.internal
② TLS wraps it 🔒 (the end-to-end §HTTPS tunnel)
③ TCP segment  src:51522 → dst:443
④ INNER IP     src:10.8.0.2 → dst:10.1.5.4   (private, not internet-routable)
   ↓ handed to utun0
⑤ VPN encrypts+authenticates the ENTIRE inner packet 🔒🔒
⑥ VPN encapsulates: OUTER IP src:client → dst:gateway, UDP:51820
   → all the internet sees: [OUTER IP|UDP|WG|🔒🔒( INNER IP|TCP|🔒TLS|HTTP )]
⑦ Gateway authenticates peer, strips outer, decrypts → inner packet dst:10.1.5.4
⑧ Firewall/ACL: "is VPN pool allowed to reach 10.1.5.4:443?" ✅ forward
⑨ Wiki server terminates the ORIGINAL TLS, sees the HTTP request, responds
```
There are now **two independent encryption layers**: inner TLS gives true end-to-end app security (even the gateway can't read the HTTP body); the outer VPN gives network-level access control + metadata privacy + authenticated admission.

## Key points
- **A VPN is fundamentally just another encapsulation below IP.** Once you see the wrap as payload, it's obvious *why* it hides the inner IP header (the metadata TLS leaks) — it's payload now.
- **Encryption ≠ access control.** Steps 2–4 give a private pipe; step 7 (making services private + a single authenticated door + segmentation ACLs) is what actually stops attackers. Re-running the [[system-design-concepts/network-security-layers|attack scenario]]: an internet scan now finds only the gateway's one hardened UDP port; `10.1.5.4` doesn't exist to the attacker → no admission → no app to exploit → no lateral movement.
- **Split vs full tunnel is a real trade-off**, not a default — split is faster but bridges two trust zones; full centralizes monitoring and hides client IP.
- **TUN (L3) vs TAP (L2)** — remote-access VPNs almost always use TUN.
- **Perimeter weakness**: once on the tunnel you're "inside," and only segmentation limits you — a compromised laptop inherits broad access. That limitation is what [[system-design-concepts/zero-trust-ztna|ZTNA]] addresses.

## Interview angle

> "A VPN encapsulates and encrypts whole IP packets at L3. Internal servers get private, non-routable IPs, and the only public thing is a hardened gateway in a DMZ that authenticates the device, decrypts, and — behind segmentation ACLs — forwards into the LAN. The build is seven steps: tunnel (packet-in-packet), encrypt, authenticate + anti-replay, ephemeral key exchange, a TUN virtual interface, split-vs-full routing, and the gateway. It's the same confidentiality/integrity/auth triad as TLS, but because it wraps the whole packet it also hides the addressing metadata HTTPS leaks, and — crucially — it enforces *network admission*, which HTTPS never does. IPsec, OpenVPN, and WireGuard are three implementations of those same steps."

## Connections
- [[theory/osi-model]] — a VPN is an L3 encapsulation; understanding nesting is the prerequisite
- [[tech/https-tls]] — same crypto triad applied to whole packets; OpenVPN reuses the TLS handshake; run TLS *inside* the tunnel for defense in depth
- [[system-design-concepts/network-security-layers]] — the payload-vs-envelope gap a VPN exists to close
- [[system-design-concepts/zero-trust-ztna]] — the evolution beyond the VPN's perimeter trust model
- [[system-design-concepts/work-distribution]] — a production VPN gateway is itself a scaled, load-balanced fleet

## Sources
- [[sources/docs/networking-deep-dive]] — §4 Building a VPN layer by layer (7 steps, protocol comparison, packet walkthrough, ZTNA)
- [networking-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/networking-deep-dive/networking-study-guide.html) — full architecture SVG (client → internet → DMZ gateway → segmented internal servers)
