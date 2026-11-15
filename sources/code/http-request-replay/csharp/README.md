# C##

A couple tips specifically if you're solving this problem using C#.

## Installing Prequisites and Running

Make sure you've installed [.NET 5.0].
In the `csharp` directory of the repo, run

```bash
dotnet restore
dotnet build
dotnet run --project RequestReplay/
```

## Visual Studio Code (Windows, Linux, OSX)

Open the directory of the repo

Open Debugging tab

Click Start (green arrow)

## Visual Studio 2017/2019 (Windows)

Open `csharp-interview-prep.sln`

Click Debug start

# Libraries

We recommend you use [Newtonsoft's Json.NET][Json.NET]
and [HttpClient] or [WebRequest] for implementation,
but you can use whichever library you want.

[HttpClient]: https://docs.microsoft.com/en-us/aspnet/web-api/overview/advanced/calling-a-web-api-from-a-net-client
[WebRequest]: https://docs.microsoft.com/en-us/dotnet/api/system.net.webrequest
[Json.NET]: https://www.newtonsoft.com/json
[.NET 5.0]: https://dotnet.microsoft.com/en-us/download/dotnet/5.0
