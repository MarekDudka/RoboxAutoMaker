#!/bin/bash
# Builds AutoMaker-<version>-x86_64.AppImage from an already-built AutoMaker
# (see the root README.md "Compiling" section: build Configuration, Stenographer,
# Language, RoboxBase, CelTechCore and AutoMaker first, in that order).
#
# What this does:
#   1. jlink's a minimal JRE (no JavaFX - that's added separately, see below).
#   2. Stages an AppDir with AutoMaker.jar + its runtime dependencies, the
#      JavaFX 21 platform jars, the bundled JRE, and the resource directories
#      (Installer/AutoMaker, Installer/Common) that AutoMaker's FakeInstallDirectory
#      points at.
#   3. Runs appimagetool to produce the final .AppImage.
#
# Requires: a JDK with `jlink` (built/tested against JDK 21+), and appimagetool
# (https://github.com/AppImage/AppImageKit/releases) on $PATH or $APPIMAGETOOL.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BUILD_DIR="$REPO_ROOT/dist/appimage-build"
OUT_DIR="$REPO_ROOT/dist"
APPDIR="$BUILD_DIR/AutoMaker.AppDir"
VERSION="$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' "$REPO_ROOT/AutoMaker/pom.xml" | head -1)"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
APPIMAGETOOL="${APPIMAGETOOL:-$(command -v appimagetool || command -v appimagetool-x86_64.AppImage || true)}"

if [ ! -f "$REPO_ROOT/AutoMaker/target/AutoMaker.jar" ]; then
    echo "AutoMaker/target/AutoMaker.jar not found - build AutoMaker (and its dependencies) first." >&2
    exit 1
fi
if [ -z "$APPIMAGETOOL" ]; then
    echo "appimagetool not found. Download it from https://github.com/AppImage/AppImageKit/releases," >&2
    echo "make it executable, and either put it on \$PATH or set \$APPIMAGETOOL to its path." >&2
    exit 1
fi

echo "Using JAVA_HOME=$JAVA_HOME"
echo "AutoMaker version: $VERSION"

rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/share/automaker/app/lib" \
         "$APPDIR/usr/share/automaker/javafx" \
         "$APPDIR/usr/share/automaker/Installer/AutoMaker" \
         "$APPDIR/usr/share/automaker/Installer/Common"
# jlink refuses to write into a directory that already exists, so it is not
# pre-created above.

# --- Minimal JRE (JavaFX is kept separate and loaded via --module-path in AppRun) ---
"$JAVA_HOME/bin/jlink" \
    --module-path "$JAVA_HOME/jmods" \
    --add-modules java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,java.net.http,java.scripting,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.jsobject,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
    --output "$APPDIR/usr/share/automaker/jre"

# --- App jar + classpath dependencies ---
cp "$REPO_ROOT/AutoMaker/target/AutoMaker.jar" "$APPDIR/usr/share/automaker/app/"
cp "$REPO_ROOT/AutoMaker/target/lib/"*.jar "$APPDIR/usr/share/automaker/app/lib/"

# --- JavaFX platform-specific (linux) modular jars, for --module-path ---
cp "$REPO_ROOT/AutoMaker/target/lib/"javafx-*-linux.jar "$APPDIR/usr/share/automaker/javafx/"

# --- Resources AutoMaker expects next to its (fake) install directory ---
# (skip the Windows/macOS/Linux self-update installer binaries - not usable here)
rsync -a --exclude 'AutoMaker-update-*' "$REPO_ROOT/Installer/AutoMaker/" "$APPDIR/usr/share/automaker/Installer/AutoMaker/"
rsync -a "$REPO_ROOT/Installer/Common/" "$APPDIR/usr/share/automaker/Installer/Common/"
chmod +x "$APPDIR/usr/share/automaker/Installer/Common/bin/"*.sh \
         "$APPDIR/usr/share/automaker/Installer/Common/Cura/CuraEngine" \
         "$APPDIR/usr/share/automaker/Installer/Common/Cura4/CuraEngine" 2>/dev/null || true

# --- AppImage plumbing (AppRun / .desktop / config template / icon) ---
cp "$SCRIPT_DIR/AppRun" "$APPDIR/AppRun"
chmod +x "$APPDIR/AppRun"
cp "$SCRIPT_DIR/AutoMaker.desktop" "$APPDIR/AutoMaker.desktop"
cp "$SCRIPT_DIR/AutoMaker.appimage.configFile.xml" "$APPDIR/usr/share/automaker/app/AutoMaker.appimage.configFile.xml"
cp "$REPO_ROOT/Installer/AutoMaker/AutoMaker.png" "$APPDIR/AutoMaker.png"

mkdir -p "$OUT_DIR"
ARCH=x86_64 "$APPIMAGETOOL" "$APPDIR" "$OUT_DIR/AutoMaker-${VERSION}-x86_64.AppImage"

echo "Built $OUT_DIR/AutoMaker-${VERSION}-x86_64.AppImage"
