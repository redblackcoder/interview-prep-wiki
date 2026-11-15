# Price-triggered purchases

Buy from a seller when a quoted price falls within a customer's limit before expiry.

## Recorded approach

Persist orders, queue due work by book, and use each order ID as the seller idempotency key. Hourly check buckets were proposed after a long order could go unchecked.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/price-triggered-purchases.md)
