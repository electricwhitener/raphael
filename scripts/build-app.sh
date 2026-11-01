#!/bin/bash
# Packages Raphael into dist/, which is what the "Run Raphael" launcher starts.
# Run this in WSL, from the project folder, after any change to the code:  scripts/build-app.sh
# It compiles into a private temp folder, not target/classes, because VS Code's Java extension also
# compiles into target/classes in the background and can leave broken class files there.
set -e
cd "$(dirname "$0")/.."
FLATLAF=$(find ~/.m2 -name 'flatlaf-3.5.4.jar' 2>/dev/null | head -1)
if [ -z "$FLATLAF" ]; then # a fresh computer: let Maven download the libraries first
    mvn -q compile
    FLATLAF=$(find ~/.m2 -name 'flatlaf-3.5.4.jar' | head -1)
fi
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/classes"
javac --release 21 -nowarn -encoding UTF-8 -cp "$FLATLAF" -d "$WORK/classes" $(find src/main/java -name '*.java')
cp -r src/main/resources/* "$WORK/classes/"

mkdir -p dist
rm -f dist/*.jar
jar --create --file dist/raphael.jar --main-class raphael.App -C "$WORK/classes" .
cp "$FLATLAF" dist/
echo "packaged: dist/raphael.jar"
