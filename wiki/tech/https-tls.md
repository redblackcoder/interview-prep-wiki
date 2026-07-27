# HTTPS & TLS

**HTTPS = HTTP over TLS.** The HTTP semantics are unchanged; every byte is carried inside an encrypted TLS tunnel running over TCP (port 443). TLS operates at [[theory/osi-model|OSI]] L5/6 and provides four guarantees:

- **Confidentiality** — a symmetric AEAD cipher (AES-GCM, ChaCha20-Poly1305) with a key negotiated in the handshake; eavesdroppers see only ciphertext.
- **Integrity** — the AEAD auth tag over every record detects any tampering (a flipped bit fails verification).
- **Authentication** — an X.509 certificate signed by a trusted CA proves you're talking to the real `bank.com`.
- **Forward secrecy** — ephemeral (EC)DHE keys mean a *later* theft of the server's private key can't decrypt *previously* captured traffic.

## How it works

### Setup order
1. **DNS** (L7 over UDP/53) resolves `bank.com` → IP. Classic DNS is **plaintext** — the first metadata leak (see [[system-design-concepts/network-security-layers]]).
2. **TCP 3-way handshake** (L4): `SYN` → `SYN-ACK` → `ACK` establishes the reliable byte stream.
3. **TLS handshake** negotiates version + cipher, authenticates the server, and agrees on symmetric keys.

### TLS 1.2 handshake — 2 round trips
```
ClientHello        →  versions, cipher list, client random, SNI=bank.com
                   ←  ServerHello (chosen cipher, server random)
                   ←  Certificate (X.509 chain)
                   ←  ServerKeyExchange (server's ephemeral ECDHE pubkey, SIGNED by cert key)
                   ←  ServerHelloDone
ClientKeyExchange  →  client's ephemeral ECDHE pubkey
        ── both sides derive the same shared secret → master secret → session keys ──
ChangeCipherSpec + Finished  →   (Finished = encrypted hash of whole handshake)
                   ←  ChangeCipherSpec + Finished
```
Only now does the (encrypted) HTTP request flow.

### TLS 1.3 handshake — 1 round trip (0-RTT on resume)
The client **guesses** the key-exchange group and sends its `key_share` in the first flight:
```
ClientHello + key_share   →
                          ←  ServerHello + key_share  (everything after here is ENCRYPTED)
                          ←  🔒{EncryptedExtensions, Certificate, CertVerify, Finished}
🔒 Finished + HTTP request →
```
TLS 1.3 also **removes all legacy/weak crypto** (RC4, CBC MACs, static-RSA key exchange, compression, renegotiation) and makes forward secrecy mandatory. Notably the **certificate is encrypted** (not sent in the clear as in 1.2).

### Cipher suite anatomy
```
TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
    │     │        │           └ hash for HKDF / handshake
    │     │        └ bulk cipher (AEAD, symmetric)
    │     └ authentication (how server proves identity)
    └ key exchange (Elliptic-Curve Diffie-Hellman Ephemeral)

TLS 1.3 keeps only the symmetric half:  TLS_AES_256_GCM_SHA384
```

### PKI — chain of trust
The OS/browser ships a **trust store** of ~150 root CA public keys. Trust chains downward: **root CA** (self-signed, in store) → signs **intermediate CA** (sent by server) → signs **leaf cert** (CN/SAN = the hostname). On receiving the `Certificate` message the client checks: (1) signature chain up to a trusted root, (2) hostname ∈ Subject Alternative Name, (3) validity window, (4) revocation (OCSP/CRL), (5) proof-of-possession — the server signs with the cert's private key.

## Key points
- **Ephemeral keys give forward secrecy.** The DH shared secret is computed independently by each side and never transmitted; the cert's RSA/ECDSA key only *signs* the exchange, it does not encrypt the secret. Discarding the ephemeral private halves is what protects captured traffic.
- **TLS 1.3 > 1.2**: 1-RTT vs 2-RTT, mandatory forward secrecy, encrypted certificate, no weak options.
- **CA trust is the soft underbelly**: you trust *every* root equally, so one compromised/coerced CA can mint a valid cert for your domain (DigiNotar, 2011). Mitigations: Certificate Transparency logs, CAA records, HSTS, public-key pinning.
- **HTTP security headers** ride inside the tunnel: `Strict-Transport-Security` (force HTTPS, block SSL-strip), `Set-Cookie: Secure; HttpOnly; SameSite`, `Content-Security-Policy`, `X-Content-Type-Options: nosniff`.
- **What TLS does *not* do**: it secures one app session's payload, not the network. The L3/L4 headers stay in the clear, and it provides no access control — the setup for [[system-design-concepts/network-security-layers]] and [[tech/vpn]].

## Interview angle

> "HTTPS is HTTP inside a TLS tunnel over TCP/443. TLS gives confidentiality (symmetric AEAD), integrity (AEAD tag), authentication (a CA-signed X.509 cert), and — via ephemeral ECDHE — forward secrecy. TLS 1.3 cut the handshake to one round trip by having the client send its key share up front, encrypted the certificate, and dropped every legacy cipher. The thing to remember is the boundary: TLS encrypts the payload but sits inside the IP/TCP headers, so it protects *what* you send, never *who's allowed to reach the server* — that's a network-layer problem."

## Connections
- [[theory/osi-model]] — TLS is L5/6, encrypting the L7 payload while the L3/L4 headers stay exposed
- [[system-design-concepts/network-security-layers]] — why this per-session encryption is necessary but not sufficient
- [[tech/vpn]] — a VPN reuses the same confidentiality/integrity/auth/forward-secrecy triad, but applies it to whole IP packets at L3; OpenVPN literally reuses the TLS handshake as its key-exchange engine

## Sources
- [[sources/docs/networking-deep-dive]] — §2 HTTPS in full detail (handshakes, cipher suites, PKI, wire format)
- [networking-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/networking-deep-dive/networking-study-guide.html) — sequence diagrams for TCP + TLS 1.2/1.3 and byte-level TLS record layout
