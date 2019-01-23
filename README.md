# BuildNotifierServer

Kotlin Code for executing Build Commands and checking each step for success or failure
If any step fails, "FAILED" will be searched for in the step output and ±100 lines will be written to a local log file and will be uploaded to Firebase Storage

Thereafter, a notification will be triggered to the target device.
