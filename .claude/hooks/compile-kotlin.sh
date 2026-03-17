#!/bin/bash
# Auto-compile after editing Kotlin files
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
CWD=$(echo "$INPUT" | jq -r '.cwd')

# Only compile when a .kt file was edited
if [[ "$FILE_PATH" == *.kt ]]; then
  cd "$CWD"
  JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin --quiet 2>&1 | tail -20
fi
