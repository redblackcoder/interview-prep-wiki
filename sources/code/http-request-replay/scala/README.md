# Scala

A couple tips specifically if you're solving this problem using Scala:

## Running

To compile and run this code with [SBT]:

```bash
$ cd http-request-replay/scala
$ sbt run
```

To run the tests:
```bash
$ sbt test
```

## Libraries

We recommend you use [Spray-json][spray-json]
or [Circe][circe] for JSON parsing
and [Scalaj HTTP][scalaj-http] for HTTP,
but you can use whichever library you want
if there is something you are more familiar with.
We've added those libraries to this project,
and included a `build.sbt` that includes them.
You don't need to use our provided `build.sbt` file;
feel free to use whichever build system you want.

Since learning a new JSON parsing library from scratch
during an interview would be quite challenging,
We've provided some code that might be helpful
under `com.practice.interview.MyLog`.
It has some case classes that might be useful
to represent the parsed Json.
Along with it, there are two companion objects
with boilerplate for each parsing library.
Hopefully this will reduce the amount of work that you need to do.

## Spray-json

[Spray Json][spray-json] is a somewhat old library that handles errors as exceptions.
If you have not used it before, this section of the documentation
is [particularly helpful](https://github.com/spray/spray-json#usage).

## Circe

[Circe][io/circe] is a newer library that is popular among functional programming enthusiasts.
The quick start documentation is not so quick,
but the Parsing Json and Encoding and Decoding sections are pretty straightforward.
The supplied boilerplate uses Semi-Automatic derivation,
which is widely used.

## Scalaj-http

There are better libraries than [Scalaj-http][scalaj-http] in Scala,
but it is very easy to learn and use for this exercise.

[spray-json]: https://github.com/spray/spray-json
[circe]: https://circe.github.io/circe/
[scalaj-http]: https://github.com/scalaj/scalaj-http
[SBT]: https://www.scala-sbt.org/
