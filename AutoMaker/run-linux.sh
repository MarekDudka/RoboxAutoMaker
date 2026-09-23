#!/bin/bash
# Launch AutoMaker on Linux with OpenJFX 21.0.4 (JavaFX is no longer bundled in the JDK).
set -e

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"

FXLIB="$(pwd)/target/lib"
FXPATH=$(ls "$FXLIB"/javafx-*.jar | tr '\n' ':')

java --module-path "$FXPATH" \
  --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing,javafx.media \
  --add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.geom=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.geom.transform=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.scene.shape=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.scene.text=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED \
  --add-opens javafx.graphics/com.sun.javafx.util=ALL-UNNAMED \
  --add-opens javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED \
  --add-opens javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
  --add-opens javafx.base/com.sun.javafx.runtime=ALL-UNNAMED \
  --add-opens javafx.base/com.sun.javafx.binding=ALL-UNNAMED \
  --add-opens javafx.base/com.sun.javafx.event=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -DlibertySystems.configFile="$(pwd)/AutoMaker.linux.configFile.xml" \
  -jar target/AutoMaker.jar "$@"
