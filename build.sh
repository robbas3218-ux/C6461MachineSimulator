#!/usr/bin/env bash
# Build, test and package the C6461 simulator.
#   ./build.sh          compile and run the regression suite
#   ./build.sh jar      also package a runnable JAR into dist/
set -e
cd "$(dirname "$0")"

echo "==> compiling"
rm -rf out && mkdir -p out
javac -d out $(find src -name '*.java')

echo "==> running regression suite"
java -cp out TestHarness

if [ "$1" = "jar" ]; then
  echo "==> packaging"
  mkdir -p dist
  # MAIN_CLASS becomes Main once the operator console exists; until then the
  # regression harness is the only entry point.
  MAIN_CLASS=${MAIN_CLASS:-TestHarness}
  jar --create --file dist/C6461MachineSimulator.jar \
      --main-class "$MAIN_CLASS" -C out .
  echo "wrote dist/C6461MachineSimulator.jar  (main class: $MAIN_CLASS)"
fi
