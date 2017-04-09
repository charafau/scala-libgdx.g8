import java.io.File

import com.google.common.base.Charsets
import com.google.common.io.Files
import sbt.IO._

import scala.collection.convert.wrapAll._

val libgdxVersion = "1.9.6"
logLevel := Level.Debug

lazy val sharedSettings: Seq[Def.Setting[_]] = Seq(
  name := "$name$",
  version := "0.1",
  scalaVersion := "$scala_version$",
  assetsDirectory := {
    val r = file("assets")
    IO.createDirectory(r)
    r
  },
  libraryDependencies ++= Seq(
    "com.badlogicgames.gdx" % "gdx" % libgdxVersion
  ),
  javacOptions ++= Seq(
    "-Xlint",
    "-encoding", "UTF-8",
    "-source", "1.7",
    "-target", "1.7"
  ),
  scalacOptions ++= Seq(
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-value-discard",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding", "UTF-8",
    "-target:jvm-1.7"
  ),
  cancelable := true,
  exportJars := true
)

lazy val core = project in file("core") settings (sharedSettings: _*)

lazy val desktop = project in file("desktop") settings (sharedSettings: _*) dependsOn core settings(
  name := (name in core).value + "-desktop",
  libraryDependencies ++= Seq(
    "net.sf.proguard" % "proguard-base" % "5.1" % "provided",
    "com.badlogicgames.gdx" % "gdx-backend-lwjgl" % libgdxVersion,
    "com.badlogicgames.gdx" % "gdx-platform" % libgdxVersion classifier "natives-desktop"
  ),
  fork in Compile := true,
  baseDirectory in run := assetsDirectory.value,
  unmanagedResourceDirectories in Compile += assetsDirectory.value,
  assembly := {
    (fullClasspath in Runtime).value // Just to make sure that compile finished
    val log = streams.value.log
    val up = (update in Compile).value
    val provided = Set(up.select(configurationFilter("provided")): _*)
    val compile = Set(up.select(configurationFilter("compile")): _*)
    val runtime = Set(up.select(configurationFilter("runtime")): _*)
    val optional = Set(up.select(configurationFilter("optional")): _*)
    val onlyProvidedNames = provided -- compile -- runtime -- optional
    val (onlyProvided, withoutProvided) = (dependencyClasspath in Compile).value.partition(cpe => onlyProvidedNames contains cpe.data)
    val exclusions = Seq("!META-INF/MANIFEST.MF", "!library.properties").mkString(",")
    val inJars = withoutProvided.map("\"" + _.data.absolutePath + "\"(" + exclusions + ")").mkString(File.pathSeparator)
    val libraryJars = onlyProvided.map("\"" + _.data.absolutePath + "\"").mkString(File.pathSeparator)
    val outfile = "\"" + (target.value / "%s-%s.jar".format((name in core).value, version.value)).absolutePath + "\""
    val classfiles = "\"" + (classDirectory in Compile).value.absolutePath + "\""
    val manifest = "\"" + file("desktop/manifest").absolutePath + "\""
    val proguardOptions = Files.readLines(file("core/proguard-project.txt"), Charsets.UTF_8) ++
      Files.readLines(file("desktop/proguard-project.txt"), Charsets.UTF_8)
    val proguard = (javaOptions in Compile).value ++ Seq("-cp", Path.makeString((managedClasspath in Compile).value.files), "proguard.ProGuard") ++ proguardOptions ++ Seq(
      "-injars", classfiles,
      "-injars", inJars,
      "-injars", manifest,
      "-libraryjars", libraryJars,
      "-outjars", outfile)
    log.info("preparing proguarded assembly")
    log.debug("Proguard command:")
    log.debug("java " + proguard.mkString(" "))
    val exitCode = Process("java", proguard) ! log
    if (exitCode != 0) {
      sys.error("Proguard failed with exit code [%s]" format exitCode)
    } else {
      log.info("Output file: " + outfile)
    }
  }
)

lazy val android = (project in file("android"))
  .enablePlugins(AndroidApp)
  .settings(sharedSettings) dependsOn core settings(
  name := (name in core).value + "-android",
  libraryDependencies ++= Seq(
    "com.badlogicgames.gdx" % "gdx-backend-android" % libgdxVersion,
    "com.badlogicgames.gdx" % "gdx-platform" % libgdxVersion classifier "natives-armeabi",
    "com.badlogicgames.gdx" % "gdx-platform" % libgdxVersion classifier "natives-armeabi-v7a",
    "com.badlogicgames.gdx" % "gdx-platform" % libgdxVersion classifier "natives-x86"
  ),
  nativesDirectory := target.value / "extractedLibs",
  extractNatives := {
    val targets = Seq("armeabi-v7a", "armeabi", "x86") //NOTE the order of armeabi-v7a and armeabi
    val libsDir = nativesDirectory.value
    val log = streams.value.log
    IO.createDirectory(libsDir)
    (managedClasspath in Compile).value.foreach(cp => {
      val dependencyName = cp.get(moduleID.key).get.name
      if (dependencyName == "gdx-platform") {
        val currentDependencyPath = cp.data.asFile
        val currentFileName = cp.data.asFile.getAbsolutePath
        val beginString = "natives-"
        val jarExtension = ".jar"
        val dependencyTarget = currentFileName.substring(
          currentFileName.indexOfSlice(beginString) + beginString.length,
          currentFileName.length - jarExtension.length)
        targets.foreach { target =>
          if (dependencyTarget.contains(target)) {
            val tempDir = IO.createTemporaryDirectory
            unzip(currentDependencyPath, tempDir)
              .foreach { temp =>
                val targetFile: File = libsDir / target / temp.getName
                IO.copyFile(temp, targetFile)
              }
          }
        }
      }
    })
  },
  projectLayout in Android := new ProjectLayout.Wrapped(ProjectLayout.Gradle(baseDirectory.value, target.value)) {
    override def assets = assetsDirectory.value

    override def jniLibs = nativesDirectory.value
  },
  Keys.`package` in Android <<= (Keys.`package` in Android) dependsOn extractNatives,
  platformTarget in Android := "android-21",
  proguardOptions in Android ++=
    Files.readLines(file("core/proguard-project.txt"), Charsets.UTF_8) ++
      Files.readLines(file("android/proguard-project.txt"), Charsets.UTF_8)
)

lazy val assetsDirectory = settingKey[File]("Directory with game's assets")

lazy val nativesDirectory = settingKey[File]("Directory where android natives are extracted to")

lazy val extractNatives = taskKey[Unit]("Extracts natives to nativesDirectory")

lazy val assembly = TaskKey[Unit]("assembly", "Assembly desktop using Proguard")

lazy val all = project in file(".") aggregate(core, desktop, android)
