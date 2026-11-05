#!/bin/sh
# Minimal bootstrap wrapper. The binary gradle-wrapper.jar could not be shipped in this archive,
# so it is fetched once from the official Gradle repository (v8.9.0 tag) on first run.
# To replace it with the official generated files at any time:  gradle wrapper --gradle-version 8.9
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -s "$JAR" ]; then
  URL="https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
  echo "Downloading gradle-wrapper.jar ..." >&2
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$JAR" "$URL"
  elif command -v wget >/dev/null 2>&1; then wget -q -O "$JAR" "$URL"
  else echo "Need curl or wget (or run: gradle wrapper --gradle-version 8.9)" >&2; exit 1; fi
fi
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
