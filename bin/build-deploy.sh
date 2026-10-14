./gradlew assembleDebug
# ワイヤレスデバッグOn
# adb pair 192.168.1.xxx:39127
adb connect 192.168.1.xxx:38383
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
