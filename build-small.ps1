<# Código feito pelo agente Copilot para criar executavel
build-small.ps1

PowerShell helper to produce small app-images for Server and Jogo by:
 1) detecting the JDK (JAVA_HOME or javac)
 2) running jdeps --print-module-deps on each jar
 3) running jlink to create a minimal runtime per jar
 4) running jpackage pointing to the runtime-image

Usage:
  powershell -ExecutionPolicy Bypass -File .\build-small.ps1

Optional parameters:
  -InputDir   : folder with JARs (default: build\input)
  -OutputDir  : folder where jpackage outputs will be written (default: build\output)
  -RuntimeDir : base folder for created runtimes (default: build\runtime)
  -JdkPath    : explicit JDK installation path (default: uses JAVA_HOME or locates javac)
  -SkipJPackage: switch to stop after creating runtimes (useful for debugging)

#>

param(
  [string]$InputDir = "build\input",
  [string]$OutputDir = "build\output",
  [string]$RuntimeDir = "build\runtime",
  [string]$JdkPath = $env:JAVA_HOME,
  [switch]$SkipJPackage
)

function Find-JdkRoot {
  param([string]$candidate)
  if ($candidate -and (Test-Path $candidate)) { return (Resolve-Path $candidate).Path }
  $javacCmd = Get-Command javac -ErrorAction SilentlyContinue
  if ($javacCmd) {
    # javac executable path: e.g. C:\Program Files\Java\jdk-21\bin\javac.exe
    $javacPath = $javacCmd.Source
    return (Split-Path -Parent (Split-Path -Parent $javacPath))
  }
  return $null
}

Write-Host "=== build-small: start ===" -ForegroundColor Cyan

# Ensure build folders exist
if (Test-Path build) {
  # keep existing build contents
} else {
  New-Item -ItemType Directory -Force -Path build | Out-Null
}
New-Item -ItemType Directory -Force -Path (Join-Path 'build' 'input') | Out-Null

Write-Host "Compiling Server and creating Server.jar..."
& javac --release 21 -d build -sourcepath . .\classes\*.java .\server\*.java
if ($LASTEXITCODE -ne 0) { Write-Error "javac failed for server sources (exit $LASTEXITCODE)"; exit 1 }
jar cfe build\input\Server.jar server.Server -C build .
if ($LASTEXITCODE -ne 0) { Write-Error "jar failed for Server.jar (exit $LASTEXITCODE)"; exit 1 }

Write-Host "Compiling Jogo and creating Jogo.jar..."
& javac --release 21 -d build -sourcepath . .\client\Jogo.java
if ($LASTEXITCODE -ne 0) { Write-Error "javac failed for client Jogo (exit $LASTEXITCODE)"; exit 1 }
jar cfe build\input\Jogo.jar client.Jogo -C build .
if ($LASTEXITCODE -ne 0) { Write-Error "jar failed for Jogo.jar (exit $LASTEXITCODE)"; exit 1 }

# get the full path to the jdeps executable
$jdepsPath = (Get-Command jdeps -ErrorAction Stop).Source
Write-Host "jdeps found at: $jdepsPath"

# derive JDK root (go up two levels from bin\jdeps.exe)
$jdkRoot = Split-Path -Parent (Split-Path -Parent $jdepsPath)
Write-Host "Detected JDK root: $jdkRoot"

Test-Path (Join-Path $jdkRoot 'bin\jlink.exe') ; Write-Host " jlink present?"
Test-Path (Join-Path $jdkRoot 'bin\jpackage.exe') ; Write-Host " jpackage present?"

$jdk = Find-JdkRoot -candidate $JdkPath
if (-not $jdk) {
  Write-Error "JDK not found. Set JAVA_HOME or ensure javac is in PATH."
  exit 1
}
Write-Host "Using JDK: $jdk"

$jdeps      = Join-Path $jdk 'bin\jdeps.exe'
$jlink      = Join-Path $jdk 'bin\jlink.exe'
$jpackage   = Join-Path $jdk 'bin\jpackage.exe'

if (-not (Test-Path $jdeps)) { Write-Error "jdeps not found at $jdeps"; exit 1 }
if (-not (Test-Path $jlink)) { Write-Warning "jlink not found at $jlink; you won't be able to create minimal runtimes." }
if (-not (Test-Path $jpackage)) { Write-Warning "jpackage not found at $jpackage; packaging may fail." }

# Ensure folders exist
if (-not (Test-Path $InputDir)) { Write-Error "Input directory '$InputDir' not found. Build your jars first."; exit 1 }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null

# application entries to process (jar name, main class, app name)
$appList = @(
  @{ Jar = 'Server.jar'; MainClass = 'server.Server'; AppName = 'Servidor' },
  @{ Jar = 'Jogo.jar'  ; MainClass = 'client.Jogo' ; AppName = 'Jogo' }
)

foreach ($app in $appList) {
  $jarPath = Join-Path $InputDir $app.Jar
  if (-not (Test-Path $jarPath)) { Write-Warning "Jar not found: $jarPath - skipping"; continue }

  Write-Host "\n--- Processing $($app.AppName) ($($app.Jar)) ---" -ForegroundColor Green

  # 1) use jdeps to get module list
  Write-Host "Running jdeps to detect modules..."
  $jdepsOutput = & $jdeps --multi-release 21 --print-module-deps $jarPath 2>&1
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "jdeps failed or returned non-zero exit code. Output:\n$jdepsOutput"
    # try a more verbose jdeps to help debugging
    Write-Host "Attempting verbose jdeps output..."
    & $jdeps --multi-release 21 --list-deps $jarPath | Out-Host
    # fallback to java.base only
    $moduleList = 'java.base'
  } else {
    $moduleList = $jdepsOutput -join ''
    $moduleList = $moduleList.Trim()
    if ([string]::IsNullOrWhiteSpace($moduleList)) { $moduleList = 'java.base' }
  }
  Write-Host "Modules: $moduleList"

  # prepare runtime output folder
  $runtimeOut = Join-Path $RuntimeDir ($app.AppName.ToLower() + '-runtime')
  if (Test-Path $runtimeOut) { Remove-Item -Recurse -Force $runtimeOut }

  if (Test-Path $jlink) {
    Write-Host "Creating runtime image at: $runtimeOut"
    $jlinkCmd = @(
      "--add-modules", $moduleList,
      "--output", $runtimeOut,
      "--strip-debug",
      "--no-header-files",
      "--no-man-pages",
      "--compress=2"
    )
    Write-Host "$jlink `"$($jlinkCmd -join ' ')`""
    & $jlink @jlinkCmd
    if ($LASTEXITCODE -ne 0) {
      Write-Warning "jlink failed for $($app.AppName). Runtime image may be incomplete.";
    }
  } else {
    Write-Warning "Skipping jlink (not available). jpackage will embed a full runtime, size will be large.";
    $runtimeOut = $null
  }

  if (-not $SkipJPackage) {
    Write-Host "Running jpackage for $($app.AppName)..."
    # jpackage will create a subfolder named after the app inside the dest folder.
    # To avoid an extra nested folder (e.g. build\output\Servidor\Servidor\app.exe)
    # pass the top-level output folder as --dest so jpackage will produce
    # build\output\<AppName>\app.exe (desired layout).
    $destRoot = (Resolve-Path $OutputDir).Path

    $jpackageArgs = @(
      "--input", (Resolve-Path $InputDir).Path,
      "--name", $app.AppName,
      "--main-jar", $app.Jar,
      "--main-class", $app.MainClass,
      "--type", "app-image",
      "--win-console",
      "--dest", $destRoot
    )
    if ($runtimeOut) { $jpackageArgs += @("--runtime-image", (Resolve-Path $runtimeOut).Path) }

    Write-Host "$jpackage `"$($jpackageArgs -join ' ')`""
    & $jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) { Write-Warning "jpackage failed for $($app.AppName). Check output above." }
  } else {
    Write-Host "--SkipJPackage specified: runtime created at $runtimeOut (if jlink succeeded)." -ForegroundColor Yellow
  }
}

Write-Host "\n=== build-small: finished ===" -ForegroundColor Cyan
