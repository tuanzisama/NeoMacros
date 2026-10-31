# T-000 构建冒烟（PowerShell）：构建 26.3 节点并断言产物完整性。
# 用法: powershell -ExecutionPolicy Bypass -File scripts\smoke-build.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "== [1/3] gradle build (:product:26.3) =="
& ./gradlew.bat ":product:26.3:build" --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "gradle build 失败" }

Write-Host "== [2/3] 定位产物 =="
$jar = Get-ChildItem -Recurse -Filter "neomacros-26.3-fabric-*.jar" |
    Where-Object { $_.FullName -match "\\build\\libs\\" -and $_.Name -notmatch "sources" } |
    Select-Object -First 1
if (-not $jar) { throw "FAIL: 未找到 neomacros-26.3-fabric-*.jar" }
Write-Host "JAR: $($jar.FullName)"

Write-Host "== [3/3] 产物断言 =="
$jarTool = Join-Path $env:JAVA_HOME "bin\jar.exe"
if (-not (Test-Path $jarTool)) { $jarTool = "jar" }
$entries = & $jarTool tf $jar.FullName

function Assert-Entry($pattern, $label) {
    if ($entries -notmatch $pattern) { throw "FAIL: $label 缺失 ($pattern)" }
    Write-Host "PASS: $label"
}

Assert-Entry "fabric\.mod\.json" "fabric.mod.json"
Assert-Entry "ink/tuanzi/neomacros/fabric/NeoMacrosFabric\.class" "入口类"

Write-Host "-- 嵌入的版本无关模块（jar-in-jar）--"
$nested = $entries | Where-Object { $_ -match "META-INF/jars/" }
if (-not $nested) {
    Write-Host "WARN: 未发现 META-INF/jars 嵌入 —— include() 未生效，需在 T-030 前核查 loom unobf 嵌入机制"
} else {
    $nested | ForEach-Object { Write-Host "  $_" }
    foreach ($m in @("doc-api", "ports", "core", "engine")) {
        if (-not ($nested -match $m)) { throw "FAIL: 嵌入 jar 缺少 $m" }
    }
    Write-Host "PASS: doc-api/ports/core/engine 全部嵌入"
}

Write-Host "SMOKE PASS: $($jar.FullName)"
