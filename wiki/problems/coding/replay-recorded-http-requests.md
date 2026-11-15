# Replay recorded HTTP requests

## Problem

Read logged request/response pairs, replay each method, path, headers and body against a test service, and compare the returned status with the recorded status. Some recorded failures are expected.

## Example

If a recorded request expects 402 and the replay returns 402, the status comparison agrees even though the request failed. The first exercise reports expected and actual codes; later dependent exchanges were a follow-up.

## Recorded approach

Deserialize each exchange, build the request with permitted headers, send it, and compare status codes including expected errors. The Java client in the saved exercise always sends POST, prints the expected and actual status, and skips the Host header. The later fixture includes GET and requests that depend on an earlier response; the saved client does not implement those steps.

[Source material](../../../sources/code/http-request-replay/java/src/main/java/com/practice/interview/Main.java)
