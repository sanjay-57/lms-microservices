# Builds and starts the whole LMS locally on Windows (no Docker).
# Prerequisites: Java 17+, Maven 3.9+, Node 18+, MySQL 8 on :3306 and RabbitMQ on :5672 already running.
# Usage (PowerShell, from the project root):  .\scripts\start-all.ps1   [-SkipBuild] [-NoFrontend]
# Each service opens in its own minimised window; close them (or run .\scripts\stop-all.ps1) to stop.
param([switch]$SkipBuild, [switch]$NoFrontend)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Logs = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = "root" }
if (-not $env:JWT_SECRET)  { $env:JWT_SECRET  = "change-this-lms-dev-secret-key-min-32-chars-long" }

$Services = [ordered]@{
  "user-service" = 8081; "course-service" = 8082; "enrollment-service" = 8083; "content-service" = 8084
  "assessment-service" = 8085; "progress-service" = 8086; "certificate-service" = 8087; "notification-service" = 8088
}

function Wait-Up($name, $url, $timeout = 180) {
  Write-Host -NoNewline ("  waiting for {0,-22}" -f $name)
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $timeout) {
    try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 $url | Out-Null; Write-Host " UP"; return } catch { Start-Sleep 2 }
  }
  Write-Host " TIMEOUT (see logs\$name.log)"
}

function Start-Svc($name) {
  $jar = Get-ChildItem (Join-Path $Root "$name\target") -Filter *.jar | Where-Object { $_.Name -notlike "*original*" } | Select-Object -First 1
  if (-not $jar) { throw "No jar for $name - run without -SkipBuild" }
  $log = Join-Path $Logs "$name.log"
  $p = Start-Process java -ArgumentList "-Xms64m","-Xmx256m","-jar",$jar.FullName -RedirectStandardOutput $log `
        -RedirectStandardError "$log.err" -WindowStyle Minimized -PassThru
  $p.Id | Out-File (Join-Path $Logs "$name.pid")
  Write-Host "  started $name (pid $($p.Id))"
}

if (-not $SkipBuild) {
  Write-Host "== Building all services"
  foreach ($svc in @("discovery-server", "api-gateway") + $Services.Keys) {
    Write-Host "  building $svc"
    Push-Location (Join-Path $Root $svc); mvn -q -B package -DskipTests; $code = $LASTEXITCODE; Pop-Location
    if ($code -ne 0) { throw "Build failed: $svc" }
  }
}

Write-Host "== Starting Eureka";        Start-Svc "discovery-server"; Wait-Up "discovery-server" "http://localhost:8761/actuator/health"
Write-Host "== Starting microservices"; foreach ($svc in $Services.Keys) { Start-Svc $svc }
foreach ($svc in $Services.Keys) { Wait-Up $svc "http://localhost:$($Services[$svc])/actuator/health" }
Write-Host "== Starting API Gateway";   Start-Svc "api-gateway"; Wait-Up "api-gateway" "http://localhost:8080/actuator/health"

if (-not $NoFrontend) {
  Write-Host "== Starting React frontend"
  Push-Location (Join-Path $Root "frontend")
  if (-not (Test-Path node_modules)) { npm install }
  Start-Process cmd -ArgumentList "/c","npm run dev" -WindowStyle Minimized
  Pop-Location
}
Write-Host "`nFrontend http://localhost:5173 | Gateway http://localhost:8080 | Eureka http://localhost:8761 | RabbitMQ http://localhost:15672"
