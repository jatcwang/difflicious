# sbt module naming convention:
core: scala 2.13 JVM
coreJS: scala 2.13 JS
coreNative: scala 2.13 Native

For scala 3, append 3 at the end such as core3

Unless you're modifying scala-2 or platform specific code, run compile / tests only for Scala 3 jvm projects
