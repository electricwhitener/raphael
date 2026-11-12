#!/usr/bin/env bash
# Builds Raphael's installer for this computer's system, with the JDK's own tools:
#   jlink    makes a small private Java runtime with only the parts Raphael needs, plus the whole
#            standard library for the programs it runs (they run on this same Java), and
#   jpackage wraps Raphael, that runtime and the icon into a native installer.
# Nothing has to be installed to run the result.
#
# Usage: scripts/package.sh [type]
#   msi (Windows), dmg (macOS), deb (Linux), or app-image (a folder, any system; the default)
# The result is in target/installer/.
set -euo pipefail
cd "$(dirname "$0")/.."
TYPE=${1:-app-image}
VERSION=$(sed -n 's:^    <version>\([0-9.]*\)</version>.*:\1:p' pom.xml | head -1)

rm -rf target/app                                              # no jar from an earlier version
mvn -B -q -DskipTests package                                  # target/app: Raphael's jar and FlatLaf
java -Djava.awt.headless=true -cp target/classes scripts/AppIcon.java target/icons

rm -rf target/runtime target/installer
# java.se: everything a student's program may import. jdk.compiler: javac, for javax.tools.
# jdk.jdi and jdk.jdwp.agent: the debugger, on both sides. jdk.charsets: files in older encodings.
# jdk.crypto.ec: the encryption HTTPS needs, for the update check.
jlink --add-modules java.se,jdk.compiler,jdk.jdi,jdk.jdwp.agent,jdk.charsets,jdk.crypto.ec \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
      --output target/runtime

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) OS=windows; ICON=target/icons/raphael.ico ;;
    Darwin) OS=mac
            iconutil -c icns target/icons/raphael.iconset -o target/icons/raphael.icns
            ICON=target/icons/raphael.icns ;;
    *) OS=linux; ICON=target/icons/raphael.png ;;
esac

ARGS=(--type "$TYPE" --name Raphael --app-version "$VERSION"
      --vendor "Raphael" --description "See data structures change while Java and C code runs"
      --input target/app --main-jar "raphael-$VERSION.jar" --main-class raphael.App
      --runtime-image target/runtime --icon "$ICON" --dest target/installer
      --app-content licenses)                                    # the licences of what's bundled
if [ "$OS" = windows ]; then
    scripts/winlibs.sh target/toolchain                         # C: gcc and a gdb with Python, in mingw64
    ARGS+=(--app-content target/toolchain/mingw64)
fi
if [ "$TYPE" != app-image ]; then
    ARGS+=(--file-associations scripts/raphael-file.properties)   # double-clicking a .raphael file opens it
    case "$OS" in
        # The upgrade code stays the same forever, so a newer Raphael installs over an older one.
        windows) ARGS+=(--win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid c52d4d34-2fc5-408b-a6bf-95ecdfc13970) ;;
        mac)     ARGS+=(--mac-package-identifier io.github.electricwhitener.raphael) ;;
        linux)   ARGS+=(--linux-shortcut --linux-menu-group Development --linux-app-category devel
                        --linux-deb-maintainer "raphael@users.noreply.github.com"
                        # GTK for the system's own file dialog; gcc, g++, the C headers and gdb (with Python) for C and C++
                        --linux-package-deps "libgtk-3-0, gcc, g++, libc6-dev, gdb") ;;
    esac
fi
jpackage "${ARGS[@]}"
ls -l target/installer
