#!/usr/bin/env bash

# Ensure any non-zero exit terminates the script immediately
set -e
set -o pipefail

rlwrap npx shadow-cljs node-repl