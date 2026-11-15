using System.Collections.Generic;

public class MyLog
{
  public MyRequest request { get; set; }
  public MyResponse response { get; set; }
}

public class MyResponse
{
  public string body { get; set; }
  public int code { get; set; }
}

public class MyRequest
{
  public string url { get; set; }
  public string method { get; set; }
  public Dictionary<string, string> headers { get; set; }
  public string body { get; set; }
}
