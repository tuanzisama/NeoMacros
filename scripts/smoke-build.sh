#!/usr/bin/env bash
# T-000 构建冒烟：构建 26.3 节点并断言产物完整性。
# 用法: ./scripts/smoke-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== [1/3] gradle build (:product:26.3) =="
./gradlew :product:26.3:build --console=plain -q

echo "== [2/3] 定位产物 =="
jar=$(find . -name 'neomacros-26.3-fabric-*.jar' ! -name '*-sources.jar' -path '*/build/libs/*' | head -1)
if [ -z "$jar" ]; then echo "FAIL: 未找到 neomacros-26.3-fabric-*.jar"; exit 1; fi
echo "JAR: $jar"

echo "== [3/3] 产物断言 =="
jar_tf() { "${JAVA_HOME:-}/bin/jar" tf "$jar" 2>/dev/null || jar tf "$jar"; }

grep_() { echo "$1" | grep -q "$2"; }

entries=$(jar_tf)

grep_ "$entries" "fabric.mod.json" || { echo "FAIL: fabric.mod.json 缺失"; exit 1; }
grep_ "$entries" "ink/tuanzi/neomacros/fabric/NeoMacrosFabric.class" || { echo "FAIL: 入口类缺失"; exit 1; }
echo "PASS: fabric.mod.json + 入口类存在"

echo "-- 嵌入的版本无关模块（jar-in-jar）--"
nested=$(echo "$entries" | grep "META-INF/jars/" || true)
if [ -z "$nested" ]; then
  echo "WARN: 未发现 META-INF/jars 嵌入 —— include() 未生效，需在 T-030 前核查 loom unobf 嵌入机制"
else
  echo "$nested"
  for m in doc-api ports core engine; do
    grep_ "$nested" "$m-" || { echo "FAIL: 嵌入 jar 缺少 $m"; exit 1; }
  done
  echo "PASS: doc-api/ports/core/engine 全部嵌入"
fi

echo "SMOKE PASS: $jar"
