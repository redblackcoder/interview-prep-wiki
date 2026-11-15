package com.practice.interview

import spray.json._
import DefaultJsonProtocol._

import io.circe.parser._
// import scalaj.http._
import cats.syntax.either._

object Main extends App{

  val strings = parseJsonWithSpray("hello Spray")
  println(strings.toJson.prettyPrint)
  
  val otherStrings = parseJsonWithCirce("hello Circe")
  println(otherStrings.toJson.prettyPrint)

  def parseJsonWithSpray(input: String): List[String] = {
    val source = s"""["$input","a","b","c"]"""
    source.parseJson.convertTo[List[String]]
  }


  def parseJsonWithCirce(input: String): List[String] = {
    val source = s"""["$input","a","b","c"]"""
      parse(source).flatMap(_.as[List[String]]).getOrElse(List.empty)
  }

}
