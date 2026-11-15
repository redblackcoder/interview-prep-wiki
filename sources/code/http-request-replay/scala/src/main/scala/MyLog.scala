package com.practice.interview


object MyLog {
  case class Request(url: String, headers: Map[String, String], body: String, method: String)
  case class Response(body: String, headers: Map[String, String], code: Int)
  case class Pair(request: Request, response: Response)
  //only useful for the second question
  case class ExtractableResponse(`object`: String, id: String)
}

object SprayJsonProtocol {
  import spray.json.DefaultJsonProtocol._, spray.json.RootJsonFormat
  import com.practice.interview.MyLog._
  implicit val responseFormat: RootJsonFormat[Response] = jsonFormat3(Response.apply)
  implicit val requestFormat: RootJsonFormat[Request] = jsonFormat4(Request.apply)
  implicit val pairFormat: RootJsonFormat[Pair] = jsonFormat2(Pair.apply)
  implicit val extractableResponse: RootJsonFormat[ExtractableResponse] = jsonFormat2(ExtractableResponse.apply)
}

object CirceDecoders {
  import io.circe._, io.circe.generic.semiauto._
  import com.practice.interview.MyLog._
  implicit val responseDecoder: Decoder[Response] = deriveDecoder[Response]
  implicit val requestDecoder: Decoder[Request] = deriveDecoder[Request]
  implicit val pairDecoder: Decoder[Pair] = deriveDecoder[Pair]
  implicit val extractableResponseDecoder: Decoder[ExtractableResponse] = deriveDecoder[ExtractableResponse]
}

