using System;
using Xunit;
using RequestReplay;

namespace tests
{
    public class UnitTest1
    {
        [Fact]
        public void TestJsonNetForSomeReason()
        {
            Assert.NotNull(
                Program.UseJsonNetForSomeReason(
                new
                {
                    Intro = "Hello",
                    Place = "World"
                }
            ));
        }

        [Fact]
        public void FailingTest()
        {
            Assert.True(false);
        }
    }
}
