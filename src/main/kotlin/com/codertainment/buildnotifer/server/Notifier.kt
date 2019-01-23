package com.codertainment.buildnotifer.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.StorageClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/*
 * Arguments
 * 0. build file to be executed (defaults to build.sh)
 * 1. device token file (defaults to deviceToken.txt)
 *
 */

class Notifier

fun main(args: Array<String>) {

  val df = SimpleDateFormat("yyyyMMdd_HHmm")

  val currentPath = Paths.get(".").toAbsolutePath().normalize().toString()
  val currentFile = File(currentPath)

  val buildFile = File(currentPath + File.separator + args.getOr(0, "build.sh"))
  val targetDevice = File(currentPath + File.separator + args.getOr(1, "deviceToken.txt")).readText()

  val logs = StringBuilder()

  var status = true
  var errorLogFile: File? = null
  val time = System.currentTimeMillis()

  var result = Pair(-1, "")
  val timeTaken = measureTimeMillis {
    result = executeFile(buildFile.name, currentFile)
  }

  logs.append(result.second)

  println("Saving Build Logs...")
  val buildLogFile = File(currentPath + File.separator + "build_${df.format(time)}.log")
  buildLogFile.writeText(logs.toString())
  println("Build Log File saved as ${buildLogFile.name}")

  if (result.first != 0) {
    status = false
    println("Failed")

    if (result.second.contains("FAILED")) {

      val lines = buildLogFile.readLines()
      var failedLine = -1

      lines.forEachIndexed { i, it ->
        if (it.contains("FAILED")) {
          failedLine = i
        }
      }

      if (failedLine != -1) {
        println("Saving Error Logs...")

        var startLine = failedLine - 100
        if (startLine < 0) startLine = 0

        var endLine = failedLine + 100
        if (endLine > lines.size) endLine = lines.size

        val errorLines = StringBuilder()
        lines.subList(startLine, endLine).forEach {
          errorLines.append(it + "\n")
        }

        errorLogFile = File(currentPath + File.separator + "error_${df.format(time)}.log")
        errorLogFile.writeText(errorLines.toString())
        println("Error Log File saved as ${errorLogFile.name}")
      }
    }
  }

  val device = currentFile.getBuildVariable("TARGET_PRODUCT")
  val buildVersion = currentFile.getBuildVariable("PLATFORM_VERSION")

  val serviceAccount = Notifier::class.java.classLoader.getResourceAsStream("google-services.json")

  val options = FirebaseOptions.Builder()
    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
    .setDatabaseUrl("https://build-notifier-5953d.firebaseio.com")
    .setStorageBucket("build-notifier-5953d.appspot.com")
    .build()

  FirebaseApp.initializeApp(options)

  var blobName: String?
  val logFile = if (errorLogFile != null) errorLogFile else buildLogFile

  try {
    val storage = StorageClient.getInstance().bucket()
    blobName = "$targetDevice/${logFile?.name}"
    println("Uploading Log...")
    storage.create(blobName, logFile?.inputStream())
  } catch (e: Exception) {
    blobName = null
    e.printStackTrace()
  }

  val message = Message.builder()
    .putData("device", device)
    .putData("time", time.toString())
    .putData("status", status.toString())
    .putData("buildVersion", buildVersion)
    .putData("timeTaken", timeTaken.toString())

  with(blobName) { message.putData("logFile", this) }

  message.setToken(targetDevice)

  println("Notifying target Device")
  val response = FirebaseMessaging.getInstance().send(message.build())

  println(response)
}

fun Array<String>.getOr(num: Int, defaultValue: String = "") = if (size > num) get(num) else defaultValue

//executes file and returns pair of (exit code, output log)
fun executeFile(fileName: String, dir: File? = null): Pair<Int, String> {

  val logBuilder = StringBuilder()
  var exitCode = -1

  val processBuilder = ProcessBuilder().apply {
    command("bash", fileName)
    dir?.let {
      directory(it)
    }
  }

  try {
    val process = processBuilder.start()

    val reader = BufferedReader(InputStreamReader(process.inputStream))

    var line: String? = ""
    while (line != null) {
      line = reader.readLine()
      println(line)
      logBuilder.append(line + "\n")
    }

    exitCode = process.waitFor()

  } catch (e: Exception) {
    e.printStackTrace()
  } finally {
    return Pair(exitCode, logBuilder.toString())
  }
}

fun File.getBuildVariable(key: String): String {
  val processBuilder = ProcessBuilder().apply {
    command("bash", "-c", "echo $$key")
    directory(this@getBuildVariable)
  }

  return try {
    val process = processBuilder.start()

    val reader = BufferedReader(InputStreamReader(process.inputStream))

    val line = reader.readText().trim()

    process.waitFor(15, TimeUnit.SECONDS)
    line
  } catch (e: Exception) {
    e.printStackTrace()
    ""
  }
}

fun isWindows() = System.getProperty("os.name").toLowerCase().contains("windows")

