---
id: sbt-plugin
title: SBT Plugin
---

# SBT Plugin

Add this to your `project/plugins.sbt`

```scala
addSbtPlugin("com.github.jatcwang" % "sbt-difflicious" % "@VERSION@")
```

Once the plugin is added to your build it is automatically enabled on all projects. 

Difflicious SBT plugin does a few things:

- Generate a Run IDs for each test run (i.e. each time you call `sbt test`)
- Setup Difflicious CLI so you can run it directly in SBT shell (`diffliciousViewer`)


