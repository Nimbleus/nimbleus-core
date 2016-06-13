organization := "com.nimbleus"

name := "nimbleus-core"

version := Versions.app

scalaVersion := "2.11.7"

resolvers ++= Seq("Nimbleus Releases" at "https://repository-nimbleus.forge.cloudbees.com/release/", "Nimbleus Snapshots" at "https://repository-nimbleus.forge.cloudbees.com/snapshot/")

credentials += Credentials(
  if (Path("/private/nimbleus/repository.credentials").exists) new File("/private/nimbleus/repository.credentials")
  else new File(Path.userHome, ".sbt/.nimbleus-credentials"))

publishTo := {
  val nimbleus = "https://repository-nimbleus.forge.cloudbees.com/"
  if (Versions.app.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nimbleus + "snapshot/")
  else
    Some("releases"  at nimbleus + "release/")
}

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





