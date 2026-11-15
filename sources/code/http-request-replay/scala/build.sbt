name := "request-replay-scala"

scalaVersion := "2.13.14"

libraryDependencies ++= Seq(
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "io.spray" %%  "spray-json" % "1.3.6",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "org.scalactic" %% "scalactic" % "3.2.19",
  "org.scalacheck" %% "scalacheck" % "1.17.0" % "test",
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % "test"
)

val circeVersion = "0.14.10"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
