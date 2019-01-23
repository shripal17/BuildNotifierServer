package com.codertainment.buildnotifer.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.StorageClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import java.io.File
import java.nio.file.Paths

/*
 * Arguments
 * 0. target device token
 * 1. device
 * 2. time
 * 3. status
 * 4. time taken

 * All below args are optional
 * 5. log file path
 * 6. step
 * 7. build version
 */

class Notifier

fun main(args: Array<String>) {

  val currentPath = Paths.get(".").toAbsolutePath().normalize().toString()

  if (args.size < 5) {
    println("Missing required args, exiting")
    return
  }

  val targetDevice = args[0]
  val device = args[1]
  val time = args[2]
  val status = args[3]
  val timeTaken = args[4]

  val logFile = File(currentPath + File.separator + args.getOrEmpty(5))
  val step = args.getOrEmpty(6)
  val buildVersion = args.getOrEmpty(7)


  val serviceAccount = Notifier::class.java.classLoader.getResourceAsStream("google-services.json")

  val options = FirebaseOptions.Builder()
    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
    .setDatabaseUrl("https://build-notifier-5953d.firebaseio.com")
    .setStorageBucket("build-notifier-5953d.appspot.com")
    .build()

  FirebaseApp.initializeApp(options)

  var uploadResult = logFile.exists() && logFile.canRead()
  var blobName = ""

  if (uploadResult) {
    try {
      val storage = StorageClient.getInstance().bucket()
      blobName = "$targetDevice/${logFile.name}"
      storage.create(blobName, logFile.inputStream())
    } catch (e: Exception) {
      uploadResult = false
      e.printStackTrace()
    }
  }

  val message = Message.builder()
    .putData("device", device)
    .putData("time", time)
    .putData("status", status)
    .putData("currentStep", step)
    .putData("buildVersion", buildVersion)
    .putData("timeTaken", timeTaken)

  if (uploadResult) message.putData("logFile", blobName)

  message.setToken(targetDevice)

  val response = FirebaseMessaging.getInstance().send(message.build())

  println(response)
}

fun Array<String>.getOrEmpty(num: Int) = if (size > num) get(num) else ""
