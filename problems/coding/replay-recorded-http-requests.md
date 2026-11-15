# Replay recorded HTTP requests

Replay serialized requests and verify each response status against its recorded expectation.

## Solution summary

Deserialize each exchange, build the request with permitted headers, send it, and compare status codes including expected errors. The saved implementation covers the first replay exercise.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/http-request-replay/java/src/main/java/com/practice/interview/Main.java)
