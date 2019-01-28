package com.codertainment.buildnotifer.server

import com.diogonunes.jcdp.color.ColoredPrinter
import com.diogonunes.jcdp.color.api.Ansi
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
import kotlin.system.measureTimeMillis

/*
 * Arguments
 * 0. Device
 * 1. Build Version
 * 2. build file to be executed (defaults to build.sh)
 * 3. device token file (defaults to deviceToken.txt)
 */

class Notifier

fun main(args: Array<String>) {

  val df = SimpleDateFormat("yyyyMMdd_HHmm")

  val cp = ColoredPrinter.Builder(1, false)
    .attribute(Ansi.Attribute.BOLD)
    .foreground(Ansi.FColor.BLUE)
    .build()

  val device = args.getOr(0, "Default")
  val buildVersion = args.getOr(1, "Default")
  if (device == "Default") {
    cp.printInfo("No Device specified")
  }
  if (buildVersion == "Default") {
    cp.printWarning("No Build Version specified")
  }

  cp.printInfo("Initialising...")

  val currentPath = Paths.get(".").toAbsolutePath().normalize().toString()
  val currentDirFile = File(currentPath)

  val logsPath = currentPath + File.separator + "logs" + File.separator
  val logsFile = File(logsPath)
  if (!logsFile.exists()) {
    logsFile.mkdirs()
  }

  val keywordsToSearchFor = arrayOf("FAILED:", "Error", "ERROR", "error")

  val buildFile = File(logsPath + File.separator + args.getOr(2, "build.sh"))
  val logs = StringBuilder()

  var status = true
  var errorLogFile: File? = null

  cp.printInfo("Running ${buildFile.name}")

  val time = System.currentTimeMillis()

  var result = Pair(-1, "")
  val timeTaken = measureTimeMillis {
    result = executeFile(buildFile.name, currentDirFile)
  }

  logs.append(result.second)

  cp.printInfo("Saving Build Logs...")
  val buildLogFile = File(logsPath + File.separator + "build_${df.format(time)}.log")
  buildLogFile.writeText(logs.toString())
  cp.printSuccess("Build Log File saved as ${buildLogFile.name}")

  if (result.first != 0) {
    status = false
    cp.printError("Build Failed")

    for (keyword in keywordsToSearchFor) {
      if (errorLogFile != null) {
        break
      }
      errorLogFile = getErrorLogsForKeyword(keyword, result.second, buildLogFile.readLines(), logsPath, time, df, cp)
    }
  }

  val targetDevice = File(currentPath + File.separator + args.getOr(3, "deviceToken.txt")).readText().trim()

  if (targetDevice.isEmpty()) {
    cp.printError("No Device Token file (${args.getOr(3, "deviceToken.txt")}) found in current directory, exiting")
    return
  }

  val serviceAccount = Notifier::class.java.classLoader.getResourceAsStream("google-services.json")

  val options = FirebaseOptions.Builder()
    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
    .setDatabaseUrl("https://build-notifier-5953d.firebaseio.com")
    .setStorageBucket("build-notifier-5953d.appspot.com")
    .build()

  FirebaseApp.initializeApp(options)

  var blobName: String?

  try {
    val storage = StorageClient.getInstance().bucket()
    blobName = "$targetDevice/${buildLogFile.name}"
    cp.printInfo("Uploading Log...")
    storage.create(blobName, buildLogFile.inputStream())
  } catch (e: Exception) {
    blobName = null
    e.printStackTrace()
  }

  var errorBlobName: String? = null

  if (errorLogFile != null) {
    try {
      val storage = StorageClient.getInstance().bucket()
      errorBlobName = "$targetDevice/${errorLogFile.name}"
      cp.printInfo("Uploading Error Log...")
      storage.create(errorBlobName, errorLogFile.inputStream())
    } catch (e: Exception) {
      errorBlobName = null
      e.printStackTrace()
    }
  }

  var progress = "Unknown"
  for (line in buildLogFile.readLines().reversed()) {
    if (line.startsWith("[")) {
      progress = line.substring(1, 4)
      break
    }
  }
  progress = progress.trim()

  val message = Message.builder()
    .putData("device", device)
    .putData("time", time.toString())
    .putData("status", status.toString())
    .putData("progress", progress)
    .putData("buildVersion", buildVersion)
    .putData("timeTaken", timeTaken.toString())

  blobName?.let { message.putData("logFile", it) }

  errorBlobName?.let { message.putData("errorLogFile", it) }

  message.setToken(targetDevice)

  cp.printInfo("Notifying target Device")
  val response = FirebaseMessaging.getInstance().send(message.build())

  if (response.isNotEmpty()) {
    cp.printSuccess("Notification sent")
  } else {
    cp.printError("Failed to notify target Device")
  }
}

fun Array<String>.getOr(num: Int, defaultValue: String = "") = if (size > num) get(num) else defaultValue

//executes file and returns pair of (exit code, output log)
fun executeFile(fileName: String, dir: File? = null): Pair<Int, String> {

  val logBuilder = StringBuilder()
  var exitCode = -1

  val processBuilder = ProcessBuilder().apply {
    if (isWindows()) {
      command("wsl", "-e", "bash", fileName)
    } else {
      command("bash", fileName)
    }
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

/*
 * searches for given keyword in logs
 * If the keyword is found, saves ±100 lines from the keyword to a separate error log file and returns the saved error log file
 * else null is returned
 */
fun getErrorLogsForKeyword(keyword: String, logText: String, lines: List<String>, logsPath: String, time: Long, df: SimpleDateFormat, cp: ColoredPrinter):
    File? {
  var errorLogFile: File? = null

  if (logText.contains(keyword)) {
    var errorLine = -1

    lines.forEachIndexed { i, it ->
      if (it.contains(keyword)) {
        errorLine = i
      }
    }

    if (errorLine != -1) {
      cp.printInfo("Saving Error Logs...")

      var startLine = errorLine - 100
      if (startLine < 0) startLine = 0

      var endLine = errorLine + 100
      if (endLine > lines.size) endLine = lines.size

      val errorLines = StringBuilder()
      lines.subList(startLine, endLine).forEach {
        errorLines.append(it + "\n")
      }

      errorLogFile = File(logsPath + File.separator + "error_${df.format(time)}.log")
      errorLogFile.writeText(errorLines.toString())
      cp.printSuccess("Error Log File saved as ${errorLogFile.name}")
    }
  }
  return errorLogFile
}

fun ColoredPrinter.printError(error: String) {
  println(error, Ansi.Attribute.BOLD, Ansi.FColor.RED, Ansi.BColor.NONE)
}

fun ColoredPrinter.printInfo(info: String) {
  println(info, Ansi.Attribute.BOLD, Ansi.FColor.BLUE, Ansi.BColor.NONE)
}

fun ColoredPrinter.printWarning(info: String) {
  println(info, Ansi.Attribute.BOLD, Ansi.FColor.YELLOW, Ansi.BColor.NONE)
}

fun ColoredPrinter.printSuccess(success: String) {
  println(success, Ansi.Attribute.BOLD, Ansi.FColor.GREEN, Ansi.BColor.NONE)
}

fun isWindows() = System.getProperty("os.name").toLowerCase().contains("windows")
