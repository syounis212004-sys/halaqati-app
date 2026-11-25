#!/bin/bash
# Offline build with the kotlin-compiler-embeddable jar cached by Gradle (no kotlinc/Gradle needed).
set -e
G=${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}
HERE=$(cd "$(dirname "$0")/.." && pwd)
BROTLI=${BROTLI_JAR:-/tmp/claude-0/r2B/dec.jar}
f() { ls $G/$1 | grep -v sources | head -1; }
STD=$(f "org.jetbrains.kotlin/kotlin-stdlib/2.2.21/*/kotlin-stdlib-2.2.21.jar")
KC=$(f "org.jetbrains.kotlin/kotlin-compiler-embeddable/2.2.21/*/*.jar"):$(f "org.jetbrains.kotlin/kotlin-daemon-embeddable/2.2.21/*/*.jar"):$(f "org.jetbrains.kotlin/kotlin-reflect/1.6.10/*/kotlin-reflect-1.6.10.jar"):$(f "org.jetbrains.kotlin/kotlin-script-runtime/2.2.21/*/*.jar"):$(f "org.jetbrains/annotations/13.0/*/annotations-13.0.jar"):$(f "org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.0/*/*.jar"):$STD
mkdir -p $HERE/build/classes
java -cp "$KC" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 11 \
  -cp "$STD:$BROTLI" -d $HERE/build/classes $HERE/src/main/kotlin $HERE/src/test/kotlin
echo "$STD:$BROTLI:$HERE/build/classes" > $HERE/build/runcp
