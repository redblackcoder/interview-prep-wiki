# Distributed address geocoding

Replace unreliable coordinates in a large file while sharing geocoder results across duplicate addresses.

## Recorded approach

Partition input by address hash and shuffle matching keys to the same worker for geocoding.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/exercise-notes/distributed-address-geocoding.md)
