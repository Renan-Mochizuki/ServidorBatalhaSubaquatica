<# Código feito pelo agente Copilot
run_autoclients.ps1

Compiles `client/AutoClient.java` (into `build`) and starts one PowerShell window per
name found on the first line of `Teste.txt`.

Usage (from repo root):
  powershell -ExecutionPolicy Bypass -File .\run_autoclients.ps1

The script:
 - compiles the Java worker into the `build` folder
 - reads the first line of Teste.txt (space-separated names)
 - reads the second line (tempo ms)
 - for each name it starts a new PowerShell window that runs the worker:
     java -cp build client.AutoClient <name> <tempo> Teste.txt
#>

Write-Host "Compiling client.AutoClient..."
javac -d build client\AutoClient.java
if ($LASTEXITCODE -ne 0) {
  Write-Error "javac failed. Fix compilation errors before launching clients."; exit 1
}

if (-not (Test-Path Teste.txt)) { Write-Error "Teste.txt not found in current folder."; exit 1 }

$lines = Get-Content Teste.txt -ErrorAction Stop
if ($lines.Length -lt 2) { Write-Error "Teste.txt must have at least two lines (names + tempo)."; exit 1 }

$firstLine = $lines[0].Trim()
$tempoLine = $lines[1].Trim()

if ([string]::IsNullOrWhiteSpace($firstLine)) { Write-Error "First line empty (names)."; exit 1 }
if (-not ([int]::TryParse($tempoLine, [ref]0))) { Write-Host "Warning: tempo line is not a number; using 200ms"; $tempoLine = "200" }

$names = $firstLine -split '\s+' | Where-Object { $_ -ne '' }

foreach ($n in $names) {
  Write-Host "Starting client for: $n"
  # Start new PowerShell window that runs the Java worker and keeps the window open
  $cmd = "java -cp build client.AutoClient '$n' $tempoLine 'Teste.txt'"
  Start-Process powershell -ArgumentList "-NoExit","-Command",$cmd
}

Write-Host "Launched $($names.Count) clients."
