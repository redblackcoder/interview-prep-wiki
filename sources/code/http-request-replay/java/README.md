# Java

A couple tips specifically if you're solving this problem using Java:

## Running

Some combination of this should execute the sample Java project
using [Maven](https://maven.apache.org/):

```bash
$ cd http-request-replay/java
$ mvn clean -e install
$ mvn -e exec:java
```

## Using Eclipse

To open the project in Eclipse:

- Navigate to: `File > Import > Maven > Existing Maven Project`
- For "Root Directory" select the `request-replay/java` folder
- Make sure the project is selected and click "Finish"

After a few moments, the project will appear in the navigator under "sample".

## Using IntelliJ IDEA

To open the project in IntelliJ IDEA:

- Ensure the bundled "Maven Integration" plugin is enabled.
- Select `File > New > Project from Existing Sources...` from the menu.
- Navigate in the file selector to this `java` subdirectory.
- Ensure "Import project from external model" and "Maven" are selected, click Next.
- You can accept the defaults on the import project dialog. Click Next.
- Leave the profile unselected. Click Next.
- The `com.practice.interview:sample:HEAD-SNAPSHOT` project should be selected. If not, select it. Click Next.
- Set Project SDK to JDK 1.8. Click Next.
- Pick a name for the project or just accept the default. Click Finish.

A new window should then appear with the project.

## Libraries

We recommend you use [Google's GSON][google/gson]
and [Square's OkHttp][square/okhttp] for implementation,
but you can use whichever library you want.
We've added both libraries to this project,
and provided a `pom.xml` that includes them.
You don't need to use our provided `pom.xml` file;
feel free to use whichever format you want.

## GSON

The json library tries to parse classes from the JSON. We've provided a class
that can be parsed from the JSON -- `com.practice.interview.MyLog`.
Hopefully this will reduce the amount of work that you need to do.

[google/gson]: https://github.com/google/gson
[square/okhttp]: https://github.com/square/okhttp
