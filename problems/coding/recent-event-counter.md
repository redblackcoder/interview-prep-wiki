# Recent event counter

Count events in a rolling five-minute window.

## Recorded approach

Append timestamped events to a deque and remove entries strictly older than the window start. An event exactly at the cutoff still counts.

[Saved Java excerpt](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/Count%20Events.md)
