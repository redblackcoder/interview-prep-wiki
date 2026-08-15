# Disagreement: Customer-Proxy Connectivity for Zero-Copy (Staff+ story)

**Prompt fit:** "Tell me about a time you disagreed with someone more senior" / "disagreed with a technical decision" / "had to influence without authority." Also serves "drove a decision under ambiguity" and "changed your mind."

> Sanitized for the wiki: customer anonymized ("a major enterprise customer"), colleagues by role, deal size approximate. Real names/threads live only in the private source note.

## Situation & stakes (lead with this)
- A **~$4M multi-cloud MarTech pursuit** hinged on standing up a **Zero-Copy** connection between our data platform (Data Cloud) and the customer's **Databricks** instance.
- **Why the whole pursuit, not one line item:** Data Cloud was the **foundation the rest of the stack stood on**. The customer's data lived in Databricks; Zero-Copy was how it federated *into* Data Cloud without copying. **Marketing Cloud** (segmentation/activation) and **Agentforce** (sales/service agents) all operate *on top of* that unified data — so with no connection, there's no customer data in Data Cloud, and the downstream products have nothing to act on. The connectivity blocker put the **anchor product at risk, and with it the bundle**.
- Their Databricks sat behind a **customer-run forward proxy with IP whitelisting** — a topology our connector framework had **never supported**. Uncharted for the team.
- I was **not on this originally** — I was **pulled in after ~2 weeks of no path forward**, so it was already a stalled, escalated, high-visibility situation.
- It wasn't just this account: the product team had **several other enterprise customers blocked on the same private-connectivity gap** (regulated finance/healthcare/telecom), representing a **multi-million-dollar pipeline**. Whatever we decided would set the framework's precedent.

## Highlights by prompt — pick the one that matches the question

One story, five openers. The interviewer's exact wording changes which beat leads and which details you spend your ~30 seconds on. **Match the opener to the prompt, then let the Q&A carry the rest.** All are honest to the same underlying facts — they differ only in emphasis. (~30s ≈ 80–110 words.)

### A. "Disagreed with someone more senior"
*Lead with the seniority gap; steelman them; show you disagreed on substance, not rank.*
> "On a stalled ~$4M deal, two Principal Architects — one who'd literally built the framework we were using — wanted to tell the customer to drop their security proxy and wait months for our Private Connect feature. Their reasoning was sound: they didn't want us owning a fragile coupling into a customer's proxy. But I pushed back — it weakened the customer's security posture, and I didn't think we'd exhausted our options. I wasn't going to move two people more tenured than me by arguing louder, so I time-boxed a day and proved an alternative worked end-to-end — then argued against my *own* solution as unsustainable to operate. We landed on an approach neither side had proposed: remove the proxy, but pair it with OIDC so no customer credentials ever lived on our side."

### B. "Disagreed with a technical decision"
*Lead with the technical substance; make your objection falsifiable, not a matter of taste.*
> "The team's proposed fix was to configure the Databricks JDBC driver's proxy parameters to get through the customer's proxy. I disagreed on a specific technical ground: those parameters bind *our own outbound* egress proxy — they can't authenticate *into* the customer's *inbound* proxy, so they physically couldn't do what we wanted. Two Principal Architects weren't convinced. Rather than debate it, I made it falsifiable — let that POC run while I built the alternative in parallel — and the data confirmed it collided with our own proxy exactly as I'd predicted. I proved a proxy-chaining approach instead, then judged *that* too unsustainable to operate, and we shipped an OIDC-based fix that sidestepped the proxy problem entirely."

### C. "Had to influence without authority"
*Lead with the fact you owned neither the architects nor the roadmap; influence = evidence + quantified business case.*
> "I had no authority here — I'd been pulled into a stalled deal late, and the people I disagreed with were two Principal Architects, one of whom owned the framework. I couldn't overrule anyone; I could only change what the decision was based on. So I did two things. First, instead of arguing my hunch, I time-boxed a day and turned it into a working end-to-end proof — evidence moves senior people that assertions don't. Second, once we had the right technical answer, I quantified the bigger picture: this same blocker was gating a pipeline of enterprise deals, not just this one. That business case is what got Private Connect re-sequenced into the current quarter — I didn't own the roadmap, I made the case that changed it."

### D. "Drove a decision under ambiguity"
*Lead with the fog — no path, uncharted, stalled; show how you imposed structure to converge.*
> "I was dropped into a deal that had been stuck for two weeks: the customer's warehouse sat behind a proxy topology our framework had never supported, and there was no documented path — every option was a maybe. Rather than let it keep circling in meetings, I imposed structure. We ran two options in parallel instead of serially — my teammate tested the architects' approach while I researched alternatives — and I time-boxed the exploration to a day so 'maybe' became 'yes or no' fast. Both answers came back quickly: their path was dead, mine worked but wasn't operable long-term. That let us converge, with data, on an OIDC interim plus accelerating the durable fix — instead of guessing."

### E. "Changed your mind" / "made a decision you later reversed"
*Lead with conviction → evidence → reversal; the point is holding an opinion loosely.*
> "I pushed hard for an option the senior architects had dismissed — a proxy-chaining approach to get through the customer's proxy without weakening their security. They weren't sold, so I spent a day and *proved it worked* end-to-end. And then I changed my own mind: once it worked, I could see it hard-coupled our infrastructure to the customer's proxy and would be expensive to operate — credential rotation especially — so I argued against shipping the very thing I'd just built. We took the durable lesson from it and landed on an OIDC-based fix instead. I'd rather spend a day to earn the right to *kill* an option than defend it because it was mine."

### (Impact / ownership variant — for "tell me about impact you drove")
*Your original opener; strongest when the prompt is about scope/impact rather than disagreement.*
> "I was pulled into a stalled ~$4M enterprise deal — two weeks in with no path forward. The whole MarTech pursuit sat on Data Cloud as the foundation, and Data Cloud needed a Zero-Copy connection into the customer's Databricks — which sat behind a proxy our framework had never supported. No connection, no customer data in the platform, nothing for Marketing Cloud or Agentforce to run on — and the same blocker was gating a line of similar enterprise deals. Two Principal Architects wanted to ask the customer to drop their proxy and wait months for Private Connect; I disagreed — it weakened their security posture and I thought we had an untested option. I time-boxed a day, proved a proxy-chaining approach worked end-to-end, then argued against my own solution as unsustainable. We shipped a more-secure interim using OIDC so no customer credentials lived on our side, and I used the escalation and the pipeline behind it to get Private Connect re-sequenced into the current quarter — trading our Iceberg REST Catalog work to next quarter."

**Choosing fast:** disagreement-with-senior → **A** · technical-decision → **B** · influence-without-authority → **C** · ambiguity → **D** · changed-your-mind → **E** · impact/scope → the variant. When in doubt for a plain "disagreement" prompt, **A** is the safest default.

## Follow-up detail (business-level)

**Why the disagreement had two phases** (worth separating — they show different skills):

- **Phase 1 — technical, falsifiable.** The architects suggested the Databricks JDBC driver's proxy parameters. I held that those configure *our own outbound egress* proxy, not authentication *into the customer's inbound* proxy — so they couldn't work here. They weren't convinced. Rather than dig in, I let my teammate run that POC (their ask deserved a real test) while I researched alternatives **in parallel**, so we weren't serialized on a path I doubted. The POC confirmed the parameters collided with our own proxy. I was right — but I'd lost nothing, and I had a backup ready.
- **Phase 2 — architectural, about the customer.** With that dead, the recommendation became "ask the customer to remove their proxy and wait for Private Connect." *This* is where I pushed back hard: it degraded the customer's security posture, and I'd found a **proxy-chaining option (`gost` multi-hop)** I believed could work. They weren't sold — it was unvalidated and meant extra work for the connectivity team.

**How I moved it without a standoff.** I didn't try to out-argue two Principal Architects on their own framework. I asked for a **time-boxed day** to turn opinion into evidence, looped in a **third (Private Connect/infra) architect** to pressure-test the long-term direction, got a **working end-to-end query** through the chained proxy, wrote it up, and convened the alignment sync myself.

**Why the resolution beat either starting position.** My POC worked — and I concluded we **shouldn't ship it**: it statically coupled our proxy config to the customer's, and operationalizing it (credential rotation especially) would be expensive for one account. So I argued to kill my own solution. We landed on a synthesis: ask the customer to remove the proxy (the architects' instinct), **but** pair it with **OIDC / IdP-based auth** so **no warehouse credentials were ever stored on our side** — which directly answered the security objection that made me resist "just remove it" — with Private Connect as the durable state.

## Impact & systemic change (close on this)
- **Deal unblocked** with a path that was *more* secure than the original proposal, not less — a better customer and sales story than "wait months" or "here's a fragile one-off."
- **Retired a bad option with data, cheaply** — proxy chaining went from "unknown" to "proven possible but operationally unsustainable," so it stopped recurring in every future proxied-warehouse conversation.
- **Systemic outcome:** the escalation + the visible pipeline of similarly-blocked enterprise accounts became the lever for a **roadmap re-sequencing decision — Private Connect (Databricks-on-Azure) pulled into the current quarter, trading our Iceberg REST Catalog work to the next quarter.** That's the honest mechanism: not "same work in half the time," but a deliberate priority swap justified by the revenue at risk — turning a one-account interim fix into the durable, reusable answer for the whole segment.

## Q&A the interviewer will probe

**They were Principal Architects — one built the framework. Why did you think you knew better?**
"I didn't frame it as knowing better. Phase one was a *falsifiable* technical claim — those JDBC params bind our outbound egress, not the customer's inbound proxy — so it was checkable, not seniority-vs-opinion, and I was happy to let it be tested. Phase two, I wasn't claiming certainty — I was flagging that we were about to make an *irreversible customer ask* on an *untested* assumption, with a high downside: weakening their security. Seniority makes a prior more reliable; it doesn't make an unvalidated option validated."

**How did you actually change their minds?**
"Evidence, time-boxed. Arguing harder wasn't going to move two people more tenured than me on their own system. A working end-to-end query in a day shifted the conversation from 'will it work' to 'should we operate it' — the right question, and one I then argued the *conservative* side of."

**You proved proxy-chaining worked, then argued to throw it away. Wasn't that wasted?**
"The opposite — that's what let us decide responsibly. The value wasn't shipping the POC; it was converting 'proxy chaining is impossible/undesirable' from an assumption into a known quantity: technically possible, operationally a bad idea, specifically coupling and credential rotation. Retiring an option with data is cheaper than shipping something unsustainable or carrying an open question into a customer commitment."

**How did you keep it from turning political?**
"Two things. I never made it about being right — I argued *for* their conservative conclusion once my own data supported it, which resets any 'he's defending his idea' read. And I didn't route around them: I widened input with the Private Connect architect and aligned in the open. Disagree loudly in the room, then commit visibly."

**Your teammate was told to build the POC you doubted — how did you handle that?**
"I didn't put him in the middle. He didn't want to ignore a request from the architects, which is legitimate — so he ran their POC while I ran the alternative in parallel. Neither path became a bottleneck, and he wasn't forced to pick sides."

**Was the whole ~$4M really at risk over one connection?**
"The bundle was anchored on Data Cloud, and Data Cloud is only useful once the customer's data is *in* it. Their data lived in Databricks, and Zero-Copy was the federation path — so that one connection was the gate for the foundational product. Marketing Cloud and Agentforce both build on top of unified Data Cloud data: segmentation and activation, sales/service agents — none of it has anything to operate on if the data never lands. So it wasn't one line item at risk; it was the base of the stack, and the pieces stacked on it. That's also why it justified pulling forward the durable fix rather than treating it as a one-off."

**Deal pressure vs. getting the architecture right?**
"The deal pressure is *why* security mattered — lighthouse account, and the pattern generalized to a pipeline of similar deals. A quiet security downgrade or a bespoke setup we couldn't operate would have cost more than the delay. OIDC gave a faster *and* more secure interim, and the pipeline justified re-sequencing the roadmap to bring the durable fix into the current quarter."

**You said Private Connect got pulled into the current quarter — what did you trade for it, and how is that not just cramming?**
"It wasn't compressing a fixed amount of work into less time — that's not credible. It was a **priority swap**: Private Connect for Databricks-on-Azure moved into the current quarter, and our **Iceberg REST Catalog** work moved to the next. The justification was opportunity cost — Private Connect was gating this deal *and* a pipeline of similarly-blocked enterprise accounts, whereas Iceberg REST Catalog had no comparable revenue attached to that specific quarter. I didn't own that roadmap unilaterally; I brought the escalation and the quantified pipeline to the architects and PM, and that framing is what made the trade an easy call. The honest version of 'I accelerated it' is 'I made the case that re-sequenced it.'"

**(Influence angle) You keep saying you had no authority — so what actually moved the decision?**
"Two levers, both about changing the *inputs* rather than pulling rank I didn't have. One, evidence: a working end-to-end proof in a day is unarguable in a way a junior-er person's opinion isn't — it reframed the debate from 'will it work' to 'should we operate it.' Two, altitude: I stopped talking about this one connection and quantified the pipeline of enterprise deals blocked on the same gap. Senior folks and the PM could act on that where they couldn't act on my hunch. Influence without authority is mostly just: bring evidence, and frame the decision at the altitude the decision-maker actually owns."

**(Changed-your-mind angle) What made you reverse on your own solution — ego didn't get in the way?**
"The opposite of ego was the point. I'd argued hard for proxy-chaining *and* proven it worked, so reversing cost me something — but the moment it worked I could see the operational reality: it statically coupled our config to the customer's proxy, and credential rotation alone would make it expensive to run for one account. Holding onto it because I'd built it would've been the ego move. The discipline I try to keep is: argue a position as hard as you can, but hold it loosely enough that new evidence — even evidence *you* generated — can kill it. I'd rather be the one to retire my own bad idea than defend it."

**(Ambiguity angle) There was no documented path — how did you avoid just thrashing?**
"By converting open-ended ambiguity into bounded, falsifiable questions and running them in parallel. Instead of 'how do we connect through this proxy?' — which invites endless meetings — I framed two concrete yes/no experiments (their JDBC-param path, my proxy-chaining path), ran them concurrently so we weren't serialized on either, and time-boxed to a day so neither could sprawl. Ambiguity thrashes when it stays abstract; it resolves when you force it into experiments with a deadline."

**What would you do differently?**
"Time-box earlier and out loud. We circled the JDBC-param path for a couple of syncs before it was empirically closed. Proposing the parallel-track — 'you test that, I'll test this, reconvene in a day' — at the *start* would have compressed a week into two days and taken heat out of the disagreement."

## Why this is staff+ (self-coaching)
- **Differentiator = the self-correction.** Most candidates tell "I disagreed, I was right, I prevailed" (senior-engineer altitude). This has the rarer shape: spend credibility to *de-risk a decision*, then spend it again to *kill your own solution* on operational grounds. That signals org-thinking over ego — the staff bar. Keep it central.
- **Also lands:** first-principles judgment used to challenge seniority *without* authority; disagree-and-commit + parallel-pathing (didn't block anyone); customer/security framing over "make it work"; cross-team orchestration (extra architect, ran the sync).
- **Delivery guardrails:** **match the opener to the prompt** (A–E above); keep only `gost`+OIDC in any spoken version, push JDBC/Squid detail to Q&A; **steelman the architects** ("their reasoning was sound") — it flips the story from "I was right" to "I understood them and still found better"; frame the technical objection as "a falsifiable claim I was happy to test," never "I told them so"; **end on the systemic change** (Private Connect re-sequenced into the quarter, trading Iceberg REST Catalog — a priority swap, *not* "same work, half the time"), not the resolved ticket.
- **Length reality check:** a true 30s is ~80–110 words. The impact variant runs ~140 words (~50s) — trim it live if the interviewer wants crisp, or use opener A/E which are already tighter.

## Connections
- [[system-design-concepts/network-security-layers]] — the crux of the disagreement: "remove your proxy" traded away the customer's envelope/perimeter security; the payload-vs-envelope and "encryption ≠ access control" framing is exactly what I argued to preserve
- [[system-design-concepts/zero-trust-ztna]] — OIDC/IdP-based auth (no stored credentials) and Private Connect are the zero-trust-shaped resolution: verified identity + private path instead of a network hole
- [[tech/https-tls]] — the customer-proxy issue was fundamentally about who terminates TLS and where trust is anchored; the OIDC endpoint call rides HTTPS
- [[tech/vpn]] — Private Connect (private cross-cloud path, no public internet) is the envelope-layer fix this story ends on

## Sources
- Personal experience — internal Slack threads across the customer pursuit + connectivity-framework support channels and the Private Connect program channel, Jul–Aug 2025 (customer name, colleague names, deal specifics, and internal links intentionally omitted here; retained privately).
