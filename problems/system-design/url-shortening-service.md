# URL shortening service

Create short, globally unique redirects and serve fast lookups across regions.

## Recorded approach

The initial proposal used truncated hashes and retried collisions. The discussion moved to region prefixes, pre-generated keys and an atomic counter, then considered enumeration of sequential IDs.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/url-shortening-service.md)
