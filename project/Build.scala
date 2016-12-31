import sbt._

object Versions {
  val app       = "0.2.3-SNAPSHOT"
  val logback   = "1.0.7"
  val ScalaTest = "2.2.6"
  val akka      = "2.4.7"
  val embeddedMongo = "1.47.1"
  val reactiveMongo = "0.11.13"
  val jodaTime = "2.1"
  val jodaConvert = "1.1"
  val scredis = "2.0.6"
  val findbugs = "2.0.1"
  val commonsLang = "3.1"
  val guava = "12.0"
  val stripe = "2.8.0"
  val jcraftSsh = "0.1.52"
  val stormPath = "1.0.RC9.2"
}

object Resolvers {
  val typeSafe                 = "Typesafe Repo"              at "http://repo.typesafe.com/typesafe/releases/"
  val ossSonatypeReleases      = "OSS Sonatype Releases"      at "https://oss.sonatype.org/content/repositories/releases"
  val ossSonatypeSnapshots     = "OSS Sonatype Snapshots"     at "https://oss.sonatype.org/content/repositories/snapshots"
}

object Compile {
  val logback = "ch.qos.logback" % "logback-classic" % Versions.logback % "compile"
  val scalaTest = "org.scalatest" %% "scalatest" % Versions.ScalaTest % "compile"
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % Versions.akka % "compile"
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Versions.akka % "compile"
  val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Versions.akka % "compile"
  val akkaSlf4J = "com.typesafe.akka" %% "akka-slf4j" % Versions.akka % "compile"
  val akkaHttpSprayJsonExperimental = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % Versions.akka  % "compile"
  val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % Versions.akka  % "compile"
  val embeddedMongo = "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % Versions.embeddedMongo  % "compile"
  val reactiveMongo = "org.reactivemongo" %% "reactivemongo" % Versions.reactiveMongo % "compile"
  val jodaTime = "joda-time" % "joda-time" % Versions.jodaTime % "compile"
  val jodaConvert = "org.joda" % "joda-convert" % Versions.jodaConvert % "compile"
  val scredis = "com.livestream" %% "scredis" % Versions.scredis % "compile"
  val findbugs = "com.google.code.findbugs" % "jsr305" % Versions.findbugs % "compile"
  val commonsLang  = "org.apache.commons" % "commons-lang3" % Versions.commonsLang % "compile"
  val guava = "com.google.guava" % "guava" % Versions.guava % "compile"
  val stripe = "com.stripe" %  "stripe-java" % Versions.stripe % "compile"
  val jcraftSsh = "com.jcraft" %"jsch" % Versions.jcraftSsh % "compile"
  val stormPathSdk = "com.stormpath.sdk" % "stormpath-sdk-api"  % Versions.stormPath % "compile"
  val stormPathHttp = "com.stormpath.sdk" % "stormpath-sdk-httpclient"  % Versions.stormPath % "compile"
}




