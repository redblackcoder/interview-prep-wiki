using System;
using Newtonsoft.Json;

namespace RequestReplay
{
  public class Program
  {
    static void Main(string[] args)
    {
      Console.WriteLine("Hello World!");
    }

    public static string UseJsonNetForSomeReason<T>(T input)
    {
      return JsonConvert.SerializeObject(input);
    }
  }
}
