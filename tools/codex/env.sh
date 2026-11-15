# Source this file: source tools/codex/env.sh
export HALAQATI_TOOLS="${HALAQATI_TOOLS:-$HOME/.local/share/halaqati}"
export JAVA_HOME="$HALAQATI_TOOLS/jdk-17"
export ANDROID_HOME="$HALAQATI_TOOLS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$HALAQATI_TOOLS/gradle-8.13/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/23.0/bin:$PATH"
