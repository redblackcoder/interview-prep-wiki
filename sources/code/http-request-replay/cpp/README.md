# C++

A couple tips specifically if you're solving this problem using C++:

## Libraries

We recommend you use [nlohmann's json library][nlohmann/json] and [cpr][cpr]
for implementation. We've added both libraries to this project, and
included a `CMakeLists.txt` that will build them (using C++17 if supported).
If you don't have a preferred CMake workflow, you can use this:

```bash
# Generate the project scaffolding into cpp/build/
cd cpp
cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug

# Build the project
cmake --build build
```

You'll need to have [libcurl](https://curl.haxx.se/download.html)
installed - we'll link it automatically.

See "Library Installation Instructions" below if don't have `cmake` or `libcurl`.

## json

The json library tries to be pretty magical. Don't be afraid to use
`.get<type>()` instead of relying on it to coerce.

Iterating over json objects is tricky. The best solution we found was
to assign the object to a `std::map<string, json>` and iterate over
that.

## cpr

If you want to set custom headers, know that `cpr::Header` is basically
just a `std::map<string, string>` - you don't have to use their initializer
list syntax. Note that the type has a custom comparison function, so it's
easiest to declare your variable as `cpr::Header`.

We also made a small change from the public docs: `cpr::Payload` has
an overloaded constructor which can take a `std::string`, instead of
only accepting a list of key/value pairs.

[nlohmann/json]: https://github.com/nlohmann/json
[cpr]: https://docs.libcpr.dev

## Library Installation Instructions

- If you're using Debian or Ubuntu, you likely want to run `sudo apt-get install cmake build-essential`.
- If you're using macOS with HomeBrew installed, you might want to run `brew install cmake curl`.

For other platforms or more detailed instructions, refer to the cpr and nlohmann/json docs.
