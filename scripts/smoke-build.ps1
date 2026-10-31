# Build smoke test (PowerShell): builds the 26.3 node and asserts artifact integrity.
# Usage: powershell -ExecutionPolicy Bypass -File scripts\smoke-build.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "== [1/3] gradle build (:product:26.3) =="
& ./gradlew.bat ":product:26.3:build" --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "gradle build failed" }

Write-Host "== [2/3] locating artifact =="
$jar = Get-ChildItem -Recurse -Filter "neomacros-26.3-fabric-*.jar" |
    Where-Object { $_.FullName -match "\\build\\libs\\" -and $_.Name -notmatch "sources" } |
    Select-Object -First 1
if (-not $jar) { throw "FAIL: neomacros-26.3-fabric-*.jar not found" }
Write-Host "JAR: $($jar.FullName)"

Write-Host "== [3/3] artifact assertions =="
$jarTool = Join-Path $env:JAVA_HOME "bin\jar.exe"
if (-not (Test-Path $jarTool)) { $jarTool = "jar" }
$entries = & $jarTool tf $jar.FullName

function Assert-Entry($pattern, $label) {
    if ($entries -notmatch $pattern) { throw "FAIL: $label missing ($pattern)" }
    Write-Host "PASS: $label"
}

Assert-Entry "fabric\.mod\.json" "fabric.mod.json"
Assert-Entry "ink/tuanzi/neomacros/fabric/NeoMacrosFabric\.class" "entrypoint class"

Write-Host "-- embedded version-independent modules (jar-in-jar) --"
$nested = $entries | Where-Object { $_ -match "META-INF/jars/" }
if (-not $nested) {
    Write-Host "WARN: no META-INF/jars entries - include() did not nest anything"
} else {
    $nested | ForEach-Object { Write-Host "  $_" }
    foreach ($m in @("doc-api", "ports", "core", "engine")) {
        if (-not ($nested -match $m)) { throw "FAIL: nested jar missing $m" }
    }
    Write-Host "PASS: doc-api/ports/core/engine all embedded"
}

Write-Host "SMOKE PASS: $($jar.FullName)"
