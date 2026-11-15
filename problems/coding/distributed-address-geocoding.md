# Distributed address geocoding

Replace unreliable coordinates in a large file while sharing geocoder results across duplicate addresses.

## Solution summary

Partition by normalized address, geocode distinct keys, then join results back to all input rows.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/worked-problems/distributed-address-geocoding.md)
