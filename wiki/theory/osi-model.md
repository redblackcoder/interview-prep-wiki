# OSI Model & Encapsulation

The **Open Systems Interconnection (OSI)** model splits network communication into seven layers, each with one well-defined job and each talking only to the layers directly above and below. That separation is why you can swap Wi-Fi for Ethernet (L1/2) without rewriting a web app (L7). The single most important consequence for security: **each layer only reads the header its peer layer wrote**, so a guarantee added at one layer (e.g. TLS encryption at L5/6) leaves the other layers' headers (e.g. IP/TCP addressing at L3/4) untouched and visible.

## The seven layers

| # | Layer | PDU | Job | Protocols |
|---|---|---|---|---|
| 7 | Application | Data | Message semantics; interface to the app | HTTP, DNS, gRPC, SMTP |
| 6 | Presentation | Data | Translation, serialization, compression, **encryption** | TLS/SSL, UTF-8, JPEG |
| 5 | Session | Data | Set up / maintain / tear down conversations; auth state | TLS session, RPC, sockets |
| 4 | Transport | **Segment** / Datagram | End-to-end delivery, reliability, ordering, **ports** | TCP, UDP, QUIC |
| 3 | Network | **Packet** | Logical addressing & routing between networks (**IP**) | IP, ICMP, IPsec, BGP |
| 2 | Data Link | **Frame** | Delivery on one physical link; **MAC** addressing | Ethernet, Wi-Fi, ARP |
| 1 | Physical | **Bits** | Raw signal on the medium | copper, fiber, radio |

Mnemonic (top→bottom): **A**ll **P**eople **S**eem **T**o **N**eed **D**ata **P**rocessing.

## How it works — encapsulation

Data flows **down** the stack on the sender and **up** on the receiver. Going down, each layer treats everything above it as an opaque **payload** and prepends its own header (L2 also appends a trailer/FCS). Going up, each layer strips its own header. The result on the wire is a set of nested dolls:

```
[ Ethernet hdr | IP hdr | TCP hdr | TLS record | HTTP request … | Ethernet FCS ]
   L2            L3       L4        L5/6          L7
```

- **L2 Ethernet** — src/dst MAC, EtherType; 14-byte header + 4-byte FCS trailer.
- **L3 IP** — src/dst IP, TTL, protocol; 20-byte min header.
- **L4 TCP** — src/dst port, seq/ack, flags; 20-byte min header.
- **L5/6 TLS** — 5-byte record header + AEAD-encrypted payload.
- **L7 HTTP** — the actual request/response.

**The security-critical observation:** TLS wraps the HTTP payload but is itself wrapped by TCP/IP/Ethernet. So the IP and TCP headers — *who is talking to whom, on what port, how big, how often* — sit **outside** the encrypted envelope. This is exactly the gap that motivates [[system-design-concepts/network-security-layers]] and the reason a VPN adds a *second* encapsulation below IP.

## OSI vs the real-world TCP/IP model

The internet actually runs on the leaner 4-layer TCP/IP model:

| OSI | TCP/IP | Protocols |
|---|---|---|
| 7 / 6 / 5 | Application | HTTP, TLS, DNS |
| 4 | Transport | TCP, UDP, QUIC |
| 3 | Internet | IP, ICMP, IPsec |
| 2 / 1 | Link | Ethernet, Wi-Fi, ARP |

## Key points
- **Know the PDU ladder cold**: Data → Segment → Packet → Frame → Bits. Interviewers probe it.
- **Addressing per layer**: L7 hostname/URL, L4 port, L3 IP, L2 MAC. A device "operates at layer N" = it makes decisions using layer-N addressing (router = L3, switch = L2, L4 load balancer = ports, L7 proxy/WAF = URLs).
- **Where TLS lives** is a classic "it depends": functionally L5/6 (session + presentation duties), running *over* TCP at L4. QUIC blurs it by folding TLS 1.3 into an L4 transport over UDP. Safe interview answer: "between transport and application, providing session + presentation."
- **Where a VPN lives**: L3 — it encapsulates and encrypts whole IP packets, which is why it protects *every* protocol and hides the inner IP header that TLS leaves exposed.

## Interview angle

> "Seven layers, and data is encapsulated top-down — each layer wraps the one above in its own header, so the receiver's layer N only reads what the sender's layer N wrote. PDUs are Data, Segment, Packet, Frame, Bits. The security consequence that matters: TLS encrypts the L7 payload but sits *inside* the IP and TCP headers, so the addressing metadata stays in the clear — that's the seam a VPN closes by adding another encapsulation at L3."

## Connections
- [[tech/https-tls]] — TLS operates at L5/6, encrypting the L7 payload while leaving L3/L4 headers exposed
- [[tech/vpn]] — a VPN adds encapsulation + encryption at L3, hiding the inner IP header OSI shows is otherwise visible
- [[system-design-concepts/network-security-layers]] — the payload-vs-envelope principle is a direct reading of the encapsulation model

## Sources
- [[sources/docs/networking-deep-dive]] — §1 OSI 7-layer model, encapsulation, OSI vs TCP/IP, where TLS/VPN sit
- [networking-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/networking-deep-dive/networking-study-guide.html) — full study guide with color-coded stack diagram and byte-level headers
