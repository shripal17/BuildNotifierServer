# BuildNotifier-Server

Kotlin Code for executing Build Script
If script fails, "FAILED"/"ERROR"/"Error"/"error" will be searched for in the step output and ±100 lines will be written to a local log file and will be uploaded to Firebase Storage

Thereafter, a notification will be triggered to the target device.

## Setup
- Download latest jar from [releases](https://github.com/shripal17/BuildNotifierServer/releases) or build one yourself and place it in your ROM's working directory
- Download the [app](https://github.com/shripal17/BuildNotifier) to your device
- Open the app -> Navigation Menu -> Device Token, click on the copy button
- Paste the copied token into a new file named `deviceToken.txt` in your ROM's working directory
- Download sample [build.sh](https://github.com/shripal17/BuildNotifierServer/blob/master/build.sh) and edit the commands as per your requirement
- *DO RUN EVERY COMMAND IN THE `run` FUNCTION SO THAT THE SCRIPT STOPS WHENEVER A STEP FAILS*
- To start building, just run
```terminal 
$ java -jar builder.jar your_device_here build_version_here
``` 
- The JAR will take care of everything:
1. Logs will be locally saved in `logs` directory
2. If Build fails, ±100 lines from the error keyword will be saved as a separate file 
3. Both these files will be uploaded to Firebase Storage
4. A notification will be sent to your device with all the details and the logs

### Note
Logs are automatically deleted from Firebase Storage after 14 days
