package com.practice.interview

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class SpraySpec extends AnyWordSpec with Matchers with Checkers {
  "spray" should {
    "parse to json" in {
      Main.parseJsonWithSpray("hello!").head shouldBe """hello!"""
    }
  }
}
