#!/bin/sh
set -e

MAVEN_VERSION=3.9.9
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
MAVEN_HOME="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_ZIP="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION-bin.zip"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -x "${JAVA_HOME:-}/bin/javac" ] && command -v javac >/dev/null 2>&1; then
  JAVAC_PATH=$(command -v javac)
  JAVA_HOME=$(cd "$(dirname "$JAVAC_PATH")/.." && pwd)
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
  fi

  mkdir -p "$WRAPPER_DIR"
  if [ ! -f "$MAVEN_ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$MAVEN_URL" -o "$MAVEN_ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$MAVEN_URL" -O "$MAVEN_ZIP"
    else
      echo "curl or wget is required to download Apache Maven $MAVEN_VERSION." >&2
      exit 1
    fi
  fi
  unzip -q -o "$MAVEN_ZIP" -d "$WRAPPER_DIR"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
