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
  val akkaProjectionVersion = "0.2"
  Seq(
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
    "com.lightbend.akka" %% "akka-projection-eventsourced" % akkaProjectionVersion,
    "com.lightbend.akka" %% "akka-projection-slick" % akkaProjectionVersion,
    "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.3",
    "com.typesafe.play" %% "play-slick" % "5.0.0",
    "org.postgresql" % "postgresql" % "42.2.14",
    "com.amazonaws" % "aws-java-sdk" % "1.7.8"
  )
}
