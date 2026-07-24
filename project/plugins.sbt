addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.7")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.0")
addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.31.0")
addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.9.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("com.siriusxm" % "sbt-snapshot4s" % "0.2.11")
addSbtPlugin("io.get-coursier" % "sbt-shading" % "2.1.7")

Compile / unmanagedSourceDirectories ++= {
  val dir = (ThisBuild / baseDirectory).value.getParentFile / "modules" / "sbt-plugin" / "src" / "main"
  Seq(dir / "scala", dir / "scala-sbt-1.0")
}
