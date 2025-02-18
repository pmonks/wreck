#!/usr/bin/env bash

# Ensure any non-zero exit terminates the script immediately
set -e
set -o pipefail

echo "ℹ️ Compiling ClojureScript tests..."
npx shadow-cljs compile test

echo "ℹ️ Running ClojureScript tests on node.js..."
node target/cljs/api-test-node.js
