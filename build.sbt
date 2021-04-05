lazy val root = Project("root", file("."))
  .aggregate(core, coretest, benchmarks)
  .settings(commonSettings, noPublishSettings)

lazy val core = Project("difflicious-core", file("modules/core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.4.2",
      "io.circe" %% "circe-generic" % "0.13.0",
      "com.propensive" %% "magnolia" % "0.17.0",
      "dev.zio" %% "izumi-reflect" % "1.0.0-M16",
      "com.softwaremill.diffx" %% "diffx-core" % "0.4.4", // FIXME:
    ),
  )

lazy val coretest = Project("coretest", file("modules/coretest"))
  .dependsOn(core)
  .settings(commonSettings, noPublishSettings)
  .settings(
    // Test deps
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.22",
    ).map(_ % Test),
  )

lazy val benchmarks = Project("benchmarks", file("modules/benchmarks"))
  .dependsOn(coretest)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)

lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := "2.13.5",
  scalacOptions --= {
    if (sys.env.get("CI").isDefined) {
      Seq.empty
    } else {
      Seq("-Xfatal-warnings")
    }
  },
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

lazy val noPublishSettings = Seq(
  publish / skip := true,
)
