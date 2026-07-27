# Zero Trust & ZTNA

A classic [[tech/vpn|VPN]] has one structural weakness: it is **perimeter-based**. Once a client is on the tunnel, it is "inside," and only network segmentation limits where it can go. If the laptop is compromised, the attacker inherits that broad network reach. **Zero Trust Network Access (ZTNA)** removes the notion of an implicitly-trusted "inside" — *"never trust, always verify"* — and grants access to *specific applications*, per request, rather than to network subnets at connect time.

## The shift

| | Traditional VPN | ZTNA |
|---|---|---|
| Trust model | Trust the network: "inside = trusted" | No implicit trust; verify every time |
| Grants access to | Network **subnets** | Specific **applications** only |
| Check frequency | Once, at connect time | Every request (identity + device posture + context) |
| Exposure | Gateway is a known public endpoint | Apps hidden behind an identity-aware proxy/broker |
| Lateral movement | Possible within the reachable segment | Denied by default — each app isolated |

## How it works

ZTNA applies the *same layered discipline* built up in [[tech/vpn|the VPN page]] — encapsulate, encrypt, authenticate, authorize — but moves the **authorization decision**:

- from *"which network are you on?"*
- to *"who are you, on what device, requesting which specific app, right now?"*

Implementations include identity-aware proxies (Google's **BeyondCorp**), mTLS **service meshes** for east-west traffic, and broker-fronted access where the application has no public endpoint at all — the client authenticates to a broker that brokers a per-session connection only to the one authorized app. Every request re-evaluates identity (SSO/MFA), device posture (managed? patched? compliant?), and context (location, time, risk signals).

## Why it closes the VPN's gap

Re-run the attack from [[system-design-concepts/network-security-layers]]: even if an endpoint is compromised, a stolen session grants access to *one app*, not a network segment. There is no flat "inside" to pivot across, and no discoverable network entry point to scan — the applications aren't addressable until an authenticated, authorized, per-request decision says so. It is the logical endpoint of the guide's thesis: **security must be enforced explicitly at every layer, per request — never assumed from position.**

## Key points
- **Perimeter trust is the flaw ZTNA fixes** — "on the network" should never imply "authorized."
- **App-scoped, not subnet-scoped** — the unit of access shrinks from a network range to a single application.
- **Continuous, contextual verification** — every request re-checks identity + device + context, not just the initial handshake.
- **Not necessarily "no VPN"** — ZTNA often still rides an encrypted tunnel; what changes is *where and how often authorization happens*.

## Interview angle

> "A VPN trusts the network — once you're on the tunnel you're inside, and only segmentation limits you, so a compromised laptop has broad reach. Zero Trust flips that: never trust, always verify. ZTNA grants access to a specific application, not a subnet, and re-checks identity, device posture, and context on every request, with apps hidden behind an identity-aware broker. Same encapsulate/encrypt/authenticate/authorize machinery — but the authorization decision moves from 'which network are you on' to 'who are you, on what device, for which app, right now,' which kills lateral movement by construction."

## Connections
- [[tech/vpn]] — ZTNA is the direct evolution of the VPN's perimeter model; both share the encrypt/authenticate/authorize toolkit
- [[system-design-concepts/network-security-layers]] — ZTNA extends the payload-vs-envelope principle into per-request authorization
- [[tech/https-tls]] — mTLS service meshes (an east-west ZTNA form) apply mutual TLS auth between every service pair
- [[system-design-concepts/work-distribution]] — identity-aware brokers are themselves a scaled, stateful fleet

## Sources
- [[sources/docs/networking-deep-dive]] — §4.6 Beyond VPN: Zero Trust (ZTNA)
- [networking-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/networking-deep-dive/networking-study-guide.html) — VPN-vs-ZTNA comparison table
