# Mask overlapping sensitive spans

Mask portions of each line identified by multiple detectors.

## Solution summary

Merge half-open spans and render each covered range once. The original processor collected spans but left rendering unfinished.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/worked-problems/mask-overlapping-sensitive-spans.md)
