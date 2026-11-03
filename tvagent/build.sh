#!/bin/sh
# 用 Docker 映像 cleanyourtv-android-build-amd64 編譯，輸出 tvagent.apk。在 Mac 上執行：sh build.sh
cd "$(dirname "$0")" && docker run --rm --platform linux/amd64 -v "$PWD":/w -w /w cleanyourtv-android-build-amd64 sh -ec '
BT=/opt/android-sdk/build-tools/34.0.0; JAR=/opt/android-sdk/platforms/android-34/android.jar
rm -rf out && mkdir -p out/classes
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias tvagent -keyalg RSA -validity 36500 -dname CN=tvagent
$BT/aapt2 link -o out/base.apk -I $JAR --manifest AndroidManifest.xml
javac -source 8 -target 8 -bootclasspath $JAR -d out/classes src/com/example/tvagent/*.java
$BT/d8 --min-api 26 --lib $JAR --output out out/classes/com/example/tvagent/*.class
(cd out && jar uf base.apk classes.dex)
$BT/zipalign -f 4 out/base.apk out/aligned.apk
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --out tvagent.apk out/aligned.apk
rm -rf out tvagent.apk.idsig'
