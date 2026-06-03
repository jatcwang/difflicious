#!/usr/bin/env -S scala-cli shebang
//> using scala "3.3.6"

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.NonFatal

final case class Config(
  firstCommit: String,
  secondCommit: String,
  warmups: Int,
  measuredRuns: Int,
  outDir: Option[Path],
  sbtCommand: Seq[String],
)

final case class TimedRun(
  label: String,
  commit: String,
  resolvedCommit: String,
  scalaVersion: String,
  phase: String,
  run: Int,
  millis: Long,
  exitCode: Int,
  log: Path,
)

final case class ProcessResult(exitCode: Int, stdout: String)

val DefaultWarmups = 2
val DefaultMeasuredRuns = 5
val ScalaRuns = List(
  "Scala 2" -> "runBenchCompile2",
  "Scala 3" -> "runBenchCompile3",
)

def usage: String =
  """Usage:
    |  scripts/benchmark-derivation-compile.scala <commit-a> <commit-b> [options]
    |
    |Options:
    |  --warmups N       Warmup runs for each commit and Scala version. Default: 2.
    |  --runs N          Measured runs for each commit and Scala version. Default: 5.
    |  --out-dir PATH    Output directory. Default: /tmp/difflicious-derivation-compile-benchmark-<timestamp>.
    |  --sbt "COMMAND"   sbt command. Default: "sbt --client".
    |  --help            Show this help.
    |
    |For each commit the script:
    |  1. checks out the commit in this worktree
    |  2. runs warmups, then measured runs
    |  3. records Scala 2 and Scala 3 separately
    |  4. uses runBenchCompile2 / runBenchCompile3 for every run
    |  5. shuts down the sbt server before moving to the next commit
    |
    |The worktree must be clean before running because the script checks out commits.
    |The original branch or commit is restored before the script exits.
    |""".stripMargin

def parseArgs(args: List[String]): Config = {
  if (args.contains("--help") || args.contains("-h")) {
    println(usage)
    sys.exit(0)
  }

  @annotation.tailrec
  def loop(
    rest: List[String],
    commits: Vector[String],
    warmups: Int,
    runs: Int,
    outDir: Option[Path],
    sbtCommand: Seq[String],
  ): Config =
    rest match {
      case Nil =>
        if (commits.size != 2) {
          fail(s"expected exactly two commits, got ${commits.size}\n\n$usage")
        }
        Config(commits(0), commits(1), warmups, runs, outDir, sbtCommand)

      case "--warmups" :: value :: tail =>
        loop(tail, commits, parsePositiveInt("--warmups", value, allowZero = true), runs, outDir, sbtCommand)

      case "--runs" :: value :: tail =>
        loop(tail, commits, warmups, parsePositiveInt("--runs", value, allowZero = false), outDir, sbtCommand)

      case "--out-dir" :: value :: tail =>
        loop(tail, commits, warmups, runs, Some(Paths.get(value).toAbsolutePath.normalize), sbtCommand)

      case "--sbt" :: value :: tail =>
        val split = value.trim.split("\\s+").filter(_.nonEmpty).toSeq
        if (split.isEmpty) fail("--sbt must not be empty")
        loop(tail, commits, warmups, runs, outDir, split)

      case option :: _ if option.startsWith("--") =>
        fail(s"unknown option: $option")

      case commit :: tail =>
        loop(tail, commits :+ commit, warmups, runs, outDir, sbtCommand)
    }

  loop(args, Vector.empty, DefaultWarmups, DefaultMeasuredRuns, None, Seq("sbt", "--client"))
}

def parsePositiveInt(option: String, value: String, allowZero: Boolean): Int =
  try {
    val parsed = value.toInt
    val ok = if (allowZero) parsed >= 0 else parsed > 0
    if (!ok) fail(s"$option must be ${if (allowZero) "non-negative" else "positive"}")
    parsed
  } catch {
    case _: NumberFormatException => fail(s"$option must be an integer, got: $value")
  }

def fail(message: String): Nothing = {
  Console.err.println(s"error: $message")
  sys.exit(1)
}

def nowTimestamp: String =
  DateTimeFormatter
    .ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.now())

def commandString(command: Seq[String]): String =
  command.map(shellQuote).mkString(" ")

def shellQuote(value: String): String =
  if (value.matches("[A-Za-z0-9_@%+=:,./-]+")) value
  else "'" + value.replace("'", "'\\''") + "'"

def runCapture(command: Seq[String], cwd: File): ProcessResult = {
  val stdout = new StringBuilder
  val stderr = new StringBuilder
  val logger = ProcessLogger(
    line => stdout.append(line).append('\n'),
    line => stderr.append(line).append('\n'),
  )
  val code = Process(command, cwd).!(logger)
  if (code != 0) {
    fail(
      s"""command failed with exit code $code: ${commandString(command)}
         |${stderr.result().trim}
         |""".stripMargin.trim,
    )
  }
  ProcessResult(code, stdout.result().trim)
}

def runLogged(command: Seq[String], cwd: File, log: Path): Int = {
  Files.createDirectories(log.getParent)
  val stream = new FileOutputStream(log.toFile, true)
  val writer = new PrintWriter(stream, true, StandardCharsets.UTF_8)
  try {
    writer.println(s"$$ ${commandString(command)}")
    writer.println(s"cwd: ${cwd.getAbsolutePath}")
    writer.println()
    Process(command, cwd).!(ProcessLogger(writer.println, writer.println))
  } finally {
    writer.close()
  }
}

def git(command: String*)(using cwd: File): String =
  runCapture("git" +: command, cwd).stdout

def ensureCleanWorktree()(using cwd: File): Unit = {
  val status = git("status", "--porcelain")
  if (status.nonEmpty) {
    fail(
      s"""worktree must be clean before checking out benchmark commits.
         |
         |Dirty files:
         |$status
         |""".stripMargin.trim,
    )
  }
}

def resolveCommit(commit: String)(using cwd: File): String =
  git("rev-parse", "--verify", s"$commit^{commit}")

def currentRef()(using cwd: File): String = {
  val branch = git("branch", "--show-current")
  if (branch.nonEmpty) branch else git("rev-parse", "HEAD")
}

def checkout(commit: String)(using cwd: File): Unit = {
  println(s"Checking out $commit")
  runCapture(Seq("git", "checkout", "--detach", commit), cwd)
}

def restore(originalRef: String)(using cwd: File): Unit = {
  println(s"Restoring $originalRef")
  runCapture(Seq("git", "checkout", originalRef), cwd)
}

def shutdownSbt(label: String, reason: String, outDir: Path)(using cwd: File, config: Config): Unit = {
  val log = outDir.resolve("logs").resolve(s"${label}_${reason}_sbt_shutdown.log")
  val exitCode = runLogged(config.sbtCommand :+ "shutdown", cwd, log)
  if (exitCode == 0) println(s"Shutdown sbt server for $label ($reason)")
  else println(s"Warning: sbt shutdown for $label ($reason) exited $exitCode; log: $log")
}

def runTimed(
  label: String,
  requestedCommit: String,
  resolvedCommit: String,
  scalaVersion: String,
  alias: String,
  phase: String,
  runNo: Int,
  outDir: Path,
)(using cwd: File, config: Config): TimedRun = {
  val safeScala = scalaVersion.toLowerCase.replace(" ", "")
  val log = outDir.resolve("logs").resolve(s"${label}_${safeScala}_${phase}_$runNo.log")
  val command = config.sbtCommand :+ alias
  val started = System.nanoTime()
  val exitCode = runLogged(command, cwd, log)
  val millis = (System.nanoTime() - started) / 1000000L

  println(f"RESULT $label%-8s $scalaVersion%-7s $phase%-8s $runNo%2d ${millis / 1000.0}%8.3fs status=$exitCode")

  val result =
    TimedRun(label, requestedCommit, resolvedCommit, scalaVersion, phase, runNo, millis, exitCode, log)

  if (exitCode != 0) {
    throw new RuntimeException(s"benchmark run failed for $label $scalaVersion $phase $runNo; log: $log")
  }

  result
}

def benchmarkCommit(
  label: String,
  requestedCommit: String,
  resolvedCommit: String,
  outDir: Path,
)(using cwd: File, config: Config): List[TimedRun] = {
  checkout(resolvedCommit)
  shutdownSbt(label, "before", outDir)

  try {
    ScalaRuns.flatMap { case (scalaVersion, alias) =>
      val warmups =
        (1 to config.warmups).map { runNo =>
          runTimed(label, requestedCommit, resolvedCommit, scalaVersion, alias, "warmup", runNo, outDir)
        }
      val measured =
        (1 to config.measuredRuns).map { runNo =>
          runTimed(label, requestedCommit, resolvedCommit, scalaVersion, alias, "measured", runNo, outDir)
        }
      warmups ++ measured
    }
  } finally {
    shutdownSbt(label, "after", outDir)
  }
}

def writeCsv(results: List[TimedRun], outDir: Path): Path = {
  val csv = outDir.resolve("results.csv")
  val writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)
  try {
    writer.write("label,requested_commit,resolved_commit,scala,phase,run,ms,status,log\n")
    results.foreach { result =>
      writer.write(
        List(
          result.label,
          result.commit,
          result.resolvedCommit,
          result.scalaVersion,
          result.phase,
          result.run.toString,
          result.millis.toString,
          result.exitCode.toString,
          result.log.toString,
        ).map(csvEscape).mkString(","),
      )
      writer.write("\n")
    }
  } finally {
    writer.close()
  }
  csv
}

def csvEscape(value: String): String =
  if (value.exists(ch => ch == ',' || ch == '"' || ch == '\n')) "\"" + value.replace("\"", "\"\"") + "\""
  else value

def describeCommit(commit: String)(using cwd: File): String =
  git("show", "--no-patch", "--format=%h %s", commit)

def stats(values: List[Long]): (Double, Double, Double, Double) = {
  val seconds = values.map(_ / 1000.0).sorted
  val mean = seconds.sum / seconds.size
  val median =
    if (seconds.size % 2 == 1) seconds(seconds.size / 2)
    else {
      val upper = seconds.size / 2
      (seconds(upper - 1) + seconds(upper)) / 2.0
    }
  (mean, median, seconds.head, seconds.last)
}

def formatSeconds(value: Double): String =
  f"$value%.3f"

def writeReport(
  config: Config,
  outDir: Path,
  csv: Path,
  results: List[TimedRun],
)(using cwd: File): Path = {
  val report = outDir.resolve("report.md")
  val measured = results.filter(_.phase == "measured")
  val grouped = measured.groupBy(result => (result.label, result.scalaVersion))
  val labels = List("commit-a", "commit-b")
  val firstResolved = results.find(_.label == "commit-a").map(_.resolvedCommit).getOrElse("")
  val secondResolved = results.find(_.label == "commit-b").map(_.resolvedCommit).getOrElse("")

  val summaryRows =
    for {
      scalaVersion <- ScalaRuns.map(_._1)
      label <- labels
      values = grouped.getOrElse((label, scalaVersion), Nil).map(_.millis)
    } yield {
      val (mean, median, min, max) = stats(values)
      s"| $label | $scalaVersion | ${values.size} | ${formatSeconds(mean)} | ${formatSeconds(median)} | ${formatSeconds(min)} | ${formatSeconds(max)} |"
    }

  val comparisonRows =
    ScalaRuns.map { case (scalaVersion, _) =>
      val first = grouped((labels(0), scalaVersion)).map(_.millis)
      val second = grouped((labels(1), scalaVersion)).map(_.millis)
      val (firstMean, _, _, _) = stats(first)
      val (secondMean, _, _, _) = stats(second)
      val delta = secondMean - firstMean
      val ratio = secondMean / firstMean
      s"| $scalaVersion | ${formatSeconds(firstMean)} | ${formatSeconds(secondMean)} | ${formatSeconds(delta)} | ${f"$ratio%.2f"}x |"
    }

  val warmupRows =
    results
      .filter(_.phase == "warmup")
      .map(result =>
        s"| ${result.label} | ${result.scalaVersion} | ${result.run} | ${formatSeconds(result.millis / 1000.0)} |",
      )

  val reportText =
    s"""# Derivation Compile Benchmark
       |
       |Output directory: `$outDir`
       |CSV: `$csv`
       |
       |## Commits
       |
       || Label | Requested | Resolved | Description |
       || --- | --- | --- | --- |
       || commit-a | `${config.firstCommit}` | `$firstResolved` | ${describeCommit(firstResolved)} |
       || commit-b | `${config.secondCommit}` | `$secondResolved` | ${describeCommit(secondResolved)} |
       |
       |Warmups per commit/Scala version: ${config.warmups}
       |Measured runs per commit/Scala version: ${config.measuredRuns}
       |sbt command: `${commandString(config.sbtCommand)}`
       |
       |Each run executes `runBenchCompile2` or `runBenchCompile3`, whose aliases clean the compile benchmark module before compiling.
       |
       |## Measured Summary
       |
       || Commit | Scala | Runs | Mean s | Median s | Min s | Max s |
       || --- | --- | ---: | ---: | ---: | ---: | ---: |
       |${summaryRows.mkString("\n")}
       |
       |## Comparison
       |
       || Scala | commit-a mean s | commit-b mean s | commit-b - commit-a s | commit-b / commit-a |
       || --- | ---: | ---: | ---: | ---: |
       |${comparisonRows.mkString("\n")}
       |
       |## Warmups
       |
       || Commit | Scala | Run | Seconds |
       || --- | --- | ---: | ---: |
       |${warmupRows.mkString("\n")}
       |""".stripMargin

  Files.writeString(report, reportText, StandardCharsets.UTF_8)
  report
}

def writeMetadata(config: Config, outDir: Path, originalRef: String)(using cwd: File): Unit = {
  val metadata =
    s"""repo=${cwd.getAbsolutePath}
       |original_ref=$originalRef
       |commit_a=${config.firstCommit}
       |commit_b=${config.secondCommit}
       |warmups=${config.warmups}
       |runs=${config.measuredRuns}
       |sbt_command=${commandString(config.sbtCommand)}
       |""".stripMargin
  Files.writeString(outDir.resolve("metadata.txt"), metadata, StandardCharsets.UTF_8)
}

@main def benchmarkDerivationCompile(args: String*): Unit =
  try runBenchmark(args.toList)
  catch {
    case NonFatal(error) =>
      Console.err.println(s"error: ${error.getMessage}")
      sys.exit(1)
  }

def runBenchmark(args: List[String]): Unit = {
  val config = parseArgs(args)
  given Config = config

  val repoRoot = runCapture(Seq("git", "rev-parse", "--show-toplevel"), new File(".")).stdout
  given File = new File(repoRoot)

  ensureCleanWorktree()

  val outDir =
    config.outDir.getOrElse(Paths.get(s"/tmp/difflicious-derivation-compile-benchmark-${nowTimestamp}"))
  Files.createDirectories(outDir.resolve("logs"))

  val originalRef = currentRef()
  val firstResolved = resolveCommit(config.firstCommit)
  val secondResolved = resolveCommit(config.secondCommit)

  writeMetadata(config, outDir, originalRef)

  println(s"Output directory: $outDir")
  println(s"commit-a: ${config.firstCommit} -> $firstResolved")
  println(s"commit-b: ${config.secondCommit} -> $secondResolved")
  println(s"warmups: ${config.warmups}")
  println(s"measured runs: ${config.measuredRuns}")
  println()

  val results =
    try {
      benchmarkCommit("commit-a", config.firstCommit, firstResolved, outDir) ++
        benchmarkCommit("commit-b", config.secondCommit, secondResolved, outDir)
    } finally {
      try restore(originalRef)
      catch {
        case NonFatal(error) =>
          Console.err.println(s"warning: failed to restore $originalRef: ${error.getMessage}")
      }
    }

  val csv = writeCsv(results, outDir)
  val report = writeReport(config, outDir, csv, results)

  println()
  println(Files.readString(report, StandardCharsets.UTF_8))
}
