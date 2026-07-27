# Network Security Layers: Payload vs Envelope

A single principle governs how the pieces of a secure system fit together: **every security property has to be placed at a specific layer, and each layer protects only what it can see.** [[tech/https-tls|HTTPS/TLS]] secures the *payload* at [[theory/osi-model|OSI]] L5–7; a [[tech/vpn|VPN]] secures the *envelope* at L3. Confusing the two — assuming "we have HTTPS, so we're secure" — is one of the most common and costly design errors.

## The core distinction

- **Payload** = the application data plus its L7/L4 framing (HTTP request, TCP stream). TLS encrypts this.
- **Envelope** = the L3/L4 addressing that routers use to deliver the packet (src/dst IP, port). Because TLS sits *inside* these headers (see [[theory/osi-model|encapsulation]]), the envelope stays **in the clear**.

> Everything from the TCP header outward — your IP, the server's IP, the port, packet sizes and timing — is plaintext metadata. TLS encrypts only the innermost payload.

## What TLS does *not* protect

1. **Metadata / the envelope** — anyone on-path (ISP, café Wi-Fi, transit AS, nation-state) sees *who talks to whom, when, how often, how much* — even if not *what*.
2. **Hostname leakage** — plaintext DNS and the TLS **SNI** field both reveal the host *before* encryption. (DoH/DoT and Encrypted ClientHello help but aren't universal.)
3. **No network access control** — TLS authenticates the *server to the client*. It does nothing to stop the whole internet from *reaching* an internal admin panel, DB port, or staging box. If it has a public IP, it's being scanned right now.
4. **Fragile CA trust** — ~150 roots trusted equally; one mis-issuance or an inspection proxy enables a valid-looking MITM.
5. **Per-service burden** — every service must implement, patch, and correctly enforce TLS + authz itself. Miss one and it's exposed.
6. **Endpoint / app** — TLS protects data *in transit*, not a phished credential, XSS, or a compromised host. "The lock icon" ≠ "safe."

**The reframing:** HTTPS answers *"is this conversation private, and is the server who it claims to be?"* It never answers *"should this person be allowed to reach this server at all?"* — a **network** question, which belongs at L3.

## A concrete attack scenario

Acme Corp exposes an internal Kibana dashboard on a public IP, "secured" with valid HTTPS + a login page. The kill chain — **every step rides valid HTTPS**:

1. **Recon** — mass-scan IPv4:443 (Shodan/masscan) finds the host. Encryption doesn't hide *existence*.
2. **Fingerprint** — the TLS cert's SAN + HTTP response title leak `Kibana 7.6.2` (internal software!).
3. **Exploit** — a CVE for that version (auth bypass / RCE) is sent *inside* a perfectly valid TLS tunnel. TLS faithfully protects the attacker's packets; it has no opinion on their content.
4. **Foothold** — shell on a box sitting in a flat corporate LAN.
5. **Pivot** — reach the DB, cloud creds, other unpatched hosts — lateral movement. HTTPS is per-connection; it has zero concept of "this actor shouldn't be inside my network."
6. **Exfil** — data leaves over :443, indistinguishable from normal web traffic, and encrypted so payload-inspecting defenses see nothing.

**Root cause:** the server was *reachable by everyone*. HTTPS secured the pipe but never asked *who is allowed to open a pipe at all*. The fix isn't more encryption — it's removing the service from the public internet behind an authenticated network gateway (a [[tech/vpn|VPN]]), and segmenting the internal network so a single foothold can't pivot.

## Defense in depth: the layers compose

The resolution is not "VPN instead of HTTPS" but **both, at their respective layers**:

- **Inner TLS (L5/6)** → true end-to-end app confidentiality; even the VPN gateway can't read the HTTP body.
- **Outer VPN (L3)** → network admission control + metadata privacy + authenticated device.

Two independent encryption layers, each answering a different question. This is why serious internal systems run TLS *inside* a VPN tunnel, and why the trajectory continues toward [[system-design-concepts/zero-trust-ztna|Zero Trust]], which pushes the admission decision down to *per request*.

## Key points
- **Encryption and access control are orthogonal.** You can have perfect encryption and still be trivially owned if the service is reachable and exploitable.
- **A public IP is a liability by default** — discoverability is automatic and continuous.
- **Flat internal networks turn one foothold into total compromise** — segmentation is not optional.
- **Metadata is sensitive on its own** — traffic analysis reveals relationships and behavior without ever decrypting a byte.

## Interview angle

> "HTTPS secures the payload but leaves the envelope — the IP/TCP headers — in the clear, and it provides no network access control. So a publicly exposed internal service with valid TLS is still discoverable, fingerprintable, and exploitable *over* that valid TLS, and a flat network lets one foothold pivot everywhere. The fix is layered: keep TLS for end-to-end payload security, but add a VPN at L3 so services are unaddressable from the internet and every connection is admitted through one authenticated gateway — then segment behind it. Encryption and access control are different questions at different layers."

## Connections
- [[theory/osi-model]] — payload-vs-envelope is a direct reading of where each layer's header sits after encapsulation
- [[tech/https-tls]] — the tool that secures the payload, and precisely what it leaves exposed
- [[tech/vpn]] — the tool that secures the envelope and enforces network admission; the fix for this attack
- [[system-design-concepts/zero-trust-ztna]] — extends the principle from network admission to per-request authorization

## Sources
- [[sources/docs/networking-deep-dive]] — §3 Why HTTPS isn't enough (six gaps + the concrete attack walkthrough)
- [networking-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/networking-deep-dive/networking-study-guide.html) — attack kill-chain diagram
