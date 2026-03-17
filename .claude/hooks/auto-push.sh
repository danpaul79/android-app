#!/bin/bash
# Auto-push after git commit succeeds
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
CWD=$(echo "$INPUT" | jq -r '.cwd')

if [[ "$COMMAND" == *"git commit"* ]]; then
  cd "$CWD"
  git push 2>&1
fi
