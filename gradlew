#!/bin/sh
JAVA_HOME_CANDIDATES="/opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
for candidate in $JAVA_HOME_CANDIDATES; do
    if [ -d "$candidate" ]; then export JAVA_HOME="$candidate"; break; fi
done

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
exec "$JAVA_HOME/bin/java" \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
