import sbt.*
import sbt.Keys.*
import com.jsuereth.sbtpgp.PgpKeys.*
import sbt.internal.LogManager
import sbt.internal.util.BufferedAppender
import java.io.PrintStream

object Build {

  val Scala212 = "2.12.21"
  val Scala213 = "2.13.18"
  val Scala3 = "3.9.0"

  // copied from: https://github.com/disneystreaming/smithy4s/blob/21a6fb04ab3485c0a4b40fe205a628c6f4750813/project/Smithy4sBuildPlugin.scala#L508
  def createBuildCommands(projects: Seq[ProjectReference]) = {
    case class Doublet(scala: String, platform: String)

    val scala3Suffix = VirtualAxis.scalaABIVersion(Scala3).idSuffix
    val scala213Suffix = VirtualAxis.scalaABIVersion(Scala213).idSuffix
    val jsSuffix = VirtualAxis.js.idSuffix
    val nativeSuffix = VirtualAxis.native.idSuffix

    val all: List[(Doublet, Seq[String])] =
      projects
        .collect { case lp: LocalProject =>
          var projectId = lp.project

          val scalaAxis =
            if (projectId.endsWith(scala3Suffix)) {
              projectId = projectId.dropRight(scala3Suffix.length)
              "3_0"
            } else "2_13"

          val platformAxis =
            if (projectId.endsWith(jsSuffix)) {
              projectId = projectId.dropRight(jsSuffix.length)
              "js"
            } else if (projectId.endsWith(nativeSuffix)) {
              projectId = projectId.dropRight(nativeSuffix.length)
              "native"
            } else {
              "jvm"
            }

          Doublet(scalaAxis, platformAxis) -> lp.project
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2))
        .toList

    // some commands, like test and compile, are setup for all modules
    val any = (t: Doublet) => true
    // things like scalafix and scalafmt are only enabled on jvm 2.13 projects
    val jvm2_13 = (t: Doublet) => t.scala == "2_13" && t.platform == "jvm"

    val jvm = (t: Doublet) => t.platform == "jvm"

    val desiredCommands: Map[String, (String, Doublet => Boolean)] = Map(
      "test" -> ("test", any),
      "compile" -> ("compile", any),
      "publishLocal" -> ("publishLocal", any),
    )

    val cmds = all.flatMap { case (doublet, projects) =>
      desiredCommands.filter(_._2._2(doublet)).map { case (name, (cmd, _)) =>
        Command.command(
          s"${name}_${doublet.scala}_${doublet.platform}",
        ) { state =>
          projects.foldLeft(state) { case (st, proj) =>
            s"$proj/$cmd" :: st
          }
        }
      }
    }

    val jvmProjects = all.collect { case (Doublet(_, "jvm"), projects) =>
      projects
    }.flatten

    cmds :+ publishLocalSnapshotCommand(jvmProjects)
  }

  // Quick local development command to run publishLocal for all JVM projects
  def publishLocalSnapshotCommand(
    projects: Seq[String],
    snapshotVersion: String = "0.7.0-SNAPSHOT",
  ): Command =
    Command.command("jvmPublishLocal") { state =>
      val matrixPublishes = projects.map(project => s"$project/publishLocal")
      // `+` cross-builds sbtPlugin over crossScalaVersions, publishing both
      // the sbt 1.x (Scala 2.12) and sbt 2.x (Scala 3) variants.
      val allPublishes = matrixPublishes :+ "+ sbtPlugin/publishLocal"
      val setVersionCmd = s"""set ThisBuild / version := "$snapshotVersion""""
      (setVersionCmd +: allPublishes).reverse.foldLeft(state) { (st, cmd) =>
        cmd :: st
      }
    }
}
