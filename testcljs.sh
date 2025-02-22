#!/usr/bin/env bash

# Ensure any non-zero exit terminates the script immediately
set -e
set -o pipefail

# Delete target because shadow-cljs doesn't seem to do a good job of cache management
rm -rf target/cljs

echo "ℹ️ Compiling ClojureScript tests..."
npx shadow-cljs compile test

echo "ℹ️ Running ClojureScript tests on node.js..."
node --enable-source-maps target/cljs/api-test-node.js
