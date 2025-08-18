addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.5")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.1")
addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.26.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.7.2") // override mdoc version from microsite
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
// addSbtPlugin("com.47deg" % "sbt-microsites" % "1.4.4")

// There are conflicts with scala-xml 1.0 vs 2.0 with microsites enabled
// libraryDependencySchemes := Seq("org.scala-lang.modules" %% "scala-xml" %VersionScheme.Always)
