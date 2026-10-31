#!/usr/bin/env bash
# Build smoke test: builds the 26.3 node and asserts artifact integrity.
# Usage: ./scripts/smoke-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== [1/3] gradle build (:product:26.3) =="
./gradlew :product:26.3:build --console=plain -q

echo "== [2/3] locating artifact =="
jar=$(find . -name 'neomacros-26.3-fabric-*.jar' ! -name '*-sources.jar' -path '*/build/libs/*' | head -1)
if [ -z "$jar" ]; then echo "FAIL: neomacros-26.3-fabric-*.jar not found"; exit 1; fi
echo "JAR: $jar"

echo "== [3/3] artifact assertions =="
jar_tf() { "${JAVA_HOME:-}/bin/jar" tf "$jar" 2>/dev/null || jar tf "$jar"; }

grep_() { echo "$1" | grep -q "$2"; }

entries=$(jar_tf)

grep_ "$entries" "fabric.mod.json" || { echo "FAIL: fabric.mod.json missing"; exit 1; }
grep_ "$entries" "ink/tuanzi/neomacros/fabric/NeoMacrosFabric.class" || { echo "FAIL: entrypoint class missing"; exit 1; }
echo "PASS: fabric.mod.json + entrypoint class present"

echo "-- embedded version-independent modules (jar-in-jar) --"
nested=$(echo "$entries" | grep "META-INF/jars/" || true)
if [ -z "$nested" ]; then
  echo "WARN: no META-INF/jars entries - include() did not nest anything"
else
  echo "$nested"
  for m in doc-api ports core engine; do
    grep_ "$nested" "$m-" || { echo "FAIL: nested jar missing $m"; exit 1; }
  done
  echo "PASS: doc-api/ports/core/engine all embedded"
fi

echo "SMOKE PASS: $jar"
