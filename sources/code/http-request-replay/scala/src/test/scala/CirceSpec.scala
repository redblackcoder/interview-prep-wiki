import com.practice.interview.{Main}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class CirceSpec extends AnyWordSpec with Matchers with Checkers {

  "circe" should {
    "parse to json" in {
      Main.parseJsonWithCirce("hello!").head shouldBe "hello!"
    }
  }
}
