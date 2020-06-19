name := "play-akka-cluster"

version := "1.0"

lazy val `clustering` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.2"

libraryDependencies ++= Seq(
  jdbc,
  cacheApi,
  ws,
  guice,
  clusterSharding,
  specs2 % Test
)

unmanagedResourceDirectories in Test += baseDirectory.value / "target/web/public/test"

libraryDependencies ++= {
  val akkaVersion = "2.6.6"
  Seq(
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
    "com.amazonaws" % "aws-java-sdk" % "1.7.8"
  )
}
