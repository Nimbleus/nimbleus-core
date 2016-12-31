organization := "com.nimbleus"

name := "nimbleus-core"

version := Versions.app

scalaVersion := "2.11.7"

credentials += Credentials(
  if (Path(Path.userHome + "/.sbt/.nimbleus-artifactory-creds").exists) {
    new File(Path.userHome, ".sbt/.nimbleus-artifactory-creds")
  } else {
    new File(".nimbleus-artifactory-creds")
  }
)

val localRelease  = "local-release"  at "https://nimbleus.jfrog.io/nimbleus/libs-release-local"
val localSnapshot = "local-snapshot" at "https://nimbleus.jfrog.io/nimbleus/libs-snapshot-local"

publishTo := {
  if (Versions.app.trim.endsWith("SNAPSHOT"))
    Some(localSnapshot)
  else
    Some(localRelease)
}

publishArtifact in Test := false

resolvers += Resolvers.ossSonatypeReleases

resolvers += Resolvers.ossSonatypeSnapshots

resolvers += Resolvers.typeSafe

libraryDependencies ++= Seq(
  Compile.logback,
  Compile.scalaTest,
  Compile.akkaActor,
  Compile.akkaCluster,
  Compile.akkaPersistence,
  Compile.embeddedMongo,
  Compile.reactiveMongo,
  Compile.jodaTime,
  Compile.jodaConvert,
  Compile.scredis,
  Compile.findbugs,
  Compile.commonsLang,
  Compile.guava,
  Compile.stripe,
  Compile.jcraftSsh,
  Compile.stormPathSdk,
  Compile.stormPathHttp,
  Compile.akkaHttpSprayJsonExperimental,
  Compile.akkaHttpTestKit,
  Compile.akkaSlf4J
)

parallelExecution in Test := false





