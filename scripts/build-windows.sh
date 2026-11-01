#!/bin/bash
# Builds the Windows app: a folder holding Raphael.exe and its own private copy of Java, so nothing
# has to be installed, and it also runs on a computer without WSL or Java.
# Run this in WSL, from the project folder:  scripts/build-windows.sh
# The first run downloads a portable Windows JDK (about 200 MB) from Adoptium, because jpackage, the
# JDK tool that makes the .exe, has to run on Windows. The JDK goes in C:\Users\<you>\raphael-build,
# and the result goes on the Windows Desktop, in "Raphael App". Delete either folder to remove it.
set -e
WIN_USER=$(cmd.exe /c "echo %USERNAME%" 2>/dev/null | tr -d '\r')
BUILD=/mnt/c/Users/$WIN_USER/raphael-build
RESULT="/mnt/c/Users/$WIN_USER/Desktop/Raphael App"
mkdir -p "$BUILD"

scripts/build-app.sh
if [ ! -f "$BUILD/jdk/bin/jpackage.exe" ]; then
    echo "downloading a portable Windows JDK 21..."
    INFO=$(curl -sL "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse")
    URL=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['binary']['package']['link'])")
    SUM=$(echo "$INFO" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['binary']['package']['checksum'])")
    curl -sL -o "$BUILD/jdk.zip" "$URL"
    echo "$SUM  $BUILD/jdk.zip" | sha256sum -c -
    python3 -I -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$BUILD/jdk.zip" "$BUILD/unpacked"
    mv "$BUILD"/unpacked/jdk-* "$BUILD/jdk"
    rm -rf "$BUILD/unpacked" "$BUILD/jdk.zip"
fi

rm -rf "$BUILD/input" "$BUILD/out"
mkdir -p "$BUILD/input"
cp dist/*.jar "$BUILD/input/"
# java.se is everything a student's program may import. jdk.compiler, jdk.jdi and jdk.jdwp.agent are
# what Raphael compiles and debugs with. --jlink-options replaces jpackage's default, which would
# remove java.exe, and Raphael needs java.exe to run the student's program in a second JVM.
( cd "$BUILD" && ./jdk/bin/jpackage.exe \
    --type app-image --name Raphael --input input --main-jar raphael.jar --main-class raphael.App --dest out \
    --add-modules java.se,jdk.compiler,jdk.jdi,jdk.jdwp.agent,jdk.unsupported,jdk.zipfs,jdk.charsets,jdk.crypto.ec \
    --jlink-options "--no-man-pages --no-header-files --strip-debug" )
rm -rf "$RESULT"
mv "$BUILD/out/Raphael" "$RESULT"
echo "done: $RESULT/Raphael.exe"
du -sh "$RESULT"
