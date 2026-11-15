# Kotlin

A couple tips specifically if you're solving this problem using Kotlin:

## Running

Run the application using [Gradle](https://gradle.org):

```bash
$ cd http-request-replay/kotlin
$ ./gradlew run
```

You can run it in debug mode with

```bash
$ ./gradlew run --debug-jvm
```

And you can run the tests with
```bash
$ ./gradlew test
```

## Using IntelliJ IDEA

To open the project in IntelliJ IDEA:

- Select `File > New > Project from Existing Sources...` from the menu.
- Navigate in the file selector to this `kotlin` subdirectory.
- Ensure "Import project from external model" and "Gradle" are selected, click Next.
- Click Finish.

A new window should then appear with the project.

## Using Android Studio

To open the project in Android Studio:

- Select `File > Open...` from the menu.
- Navigate in the file selector to this `kotlin` subdirectory.

## Libraries

We recommend you use [Google's GSON][google/gson]
and [Square's OkHttp][square/okhttp] for implementation,
but you can use whichever library you want.
We've added both libraries to this project,
and provided a `build.gradle` that includes them.

## GSON

The json library tries to parse classes from the JSON. We've provided a class
that can be parsed from the JSON -- `com.practice.interview.MyLog`.
Hopefully this will reduce the amount of work that you need to do.

[google/gson]: https://github.com/google/gson
[square/okhttp]: https://github.com/square/okhttp
