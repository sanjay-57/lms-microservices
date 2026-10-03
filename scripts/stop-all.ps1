# Stops the Java services started by start-all.ps1 (the frontend window can simply be closed).
$Logs = Join-Path (Split-Path -Parent $PSScriptRoot) "logs"
Get-ChildItem $Logs -Filter *.pid -ErrorAction SilentlyContinue | ForEach-Object {
  $id = Get-Content $_.FullName
  Stop-Process -Id $id -ErrorAction SilentlyContinue
  Write-Host "stopped $($_.BaseName) ($id)"
  Remove-Item $_.FullName
}
