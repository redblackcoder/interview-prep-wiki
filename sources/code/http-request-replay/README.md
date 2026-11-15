# Request Replay

When making API changes,
it's nice to know that you won't affect current integrations.
To attempt to validate this, we'll build a tool to replay a
series of previously-captured API requests.

Here are a few JSON files containing a series of requests and responses from
a payments-style REST API. Each entry is a JSON map with the `request` entry describing
the HTTP request to send, and the `response` entry giving a sample response.

## Your task

Write a program which, given each of these files
(start with [requestlog-charges.json](requestlog-charges.json)!),
replays each request to the API host (`api.example.com` in the logs),
and compares the real response to the request's sample response.
Since the responses we get from the API
have fields that vary (timestamps, IDs),
only check against the HTTP status code.

## What to focus on

Please write this program as you would anything that you plan on running for a
long time: Focus on maintainability and readability!

## Library recommendations

Parsing JSON and making HTTPS requests can be no fun!
We recommend these libraries for your language of choice,
but you may use different JSON or HTTP libraries, if you prefer.
This is not an exhaustive list!
Ask your interviewer if your language of choice is not on here.

| Language      | HTTP                          | JSON                      |
| ------------- | ----------------------------- | ------------------------- |
| C++           | [cpr]                         | [nlohmann/json]           |
| C#            | [HttpClient]                  | [Json.NET]                |
| Clojure       | [clj-http.client]             | [data.json]               |
| Common Lisp   | [drakma]                      | [cl-json]                 |
| Go            | [net/http]                    | [encoding/json]           |
| Java / Kotlin | [OkHttp]                      | [Gson]                    |
| JavaScript    | [node-fetch] or [axios]       | [ES5 JSON]                |
| PHP           | [curl][php-curl] or [guzzle]  | [json][php-json]          |
| Python        | [requests][py-requests]       | [json][py-json]           |
| Ruby          | [excon]                       | [JSON][ruby-JSON]         |
| Scala         | [sttp] or [http4s]            | [spray-json] or [Circe]   |
| Swift         | [url-loading]                 | [swift-decoding]          |

## Known Issues

### Importing node-fetch in JavaScript
Starting with v3, `node-fetch` no longer supports the Node `require()` syntax
and requires users to use ESM modules and the `import` syntax. This requires
users to either rename their file or pass additional configuration to Node:
https://stackoverflow.com/questions/69041454/error-require-of-es-modules-is-not-supported-when-importing-node-fetch.

For the purpose of this interview, we recommend installing v2 of `node-fetch`
instead, using `npm install node-fetch@2`.

[cpr]: https://docs.libcpr.dev
[nlohmann/json]: https://nlohmann.github.io/json/

[HttpClient]: https://docs.microsoft.com/en-us/dotnet/api/system.net.http.httpclient?view=net-5.0
[Json.NET]: https://www.newtonsoft.com/json

[clj-http.client]: https://clojars.org/clj-http
[data.json]: https://clojure.github.io/data.json/

[drakma]: https://edicl.github.io/drakma/
[cl-json]: https://common-lisp.net/project/cl-json/cl-json.html

[net/http]: https://golang.org/pkg/net/http/
[encoding/json]: https://golang.org/pkg/encoding/json/

[OkHttp]: https://square.github.io/okhttp/
[Gson]: https://github.com/google/gson/blob/master/UserGuide.md

[js-req]: https://github.com/request/request
[node-fetch]: https://github.com/bitinn/node-fetch
[axios]: https://github.com/axios/axios
[ES5 JSON]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON

[php-curl]: https://www.php.net/manual/en/book.curl.php
[guzzle]: https://docs.guzzlephp.org/en/stable/
[php-json]: https://www.php.net/manual/en/book.json.php

[py-requests]: https://requests.readthedocs.io/en/master/
[py-json]: https://docs.python.org/3/library/json.html

[excon]: https://github.com/excon/excon
[ruby-JSON]: https://ruby-doc.org/stdlib-2.6.1/libdoc/json/rdoc/JSON.html

[sttp]: https://sttp.softwaremill.com/en/stable/
[http4s]: https://github.com/http4s/http4s
[spray-json]: https://github.com/spray/spray-json
[Circe]: https://circe.github.io/circe/

[url-loading]: https://developer.apple.com/documentation/foundation/url_loading_system
[swift-decoding]: https://developer.apple.com/documentation/foundation/archives_and_serialization/encoding_and_decoding_custom_types
