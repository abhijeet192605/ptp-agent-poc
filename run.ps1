# Runs the project without a global Maven install.
# Downloads Apache Maven into .tools\ the first time, then runs the command you pass.
#
#   .\run.ps1 test              -> runs the unit tests (no Ollama needed)
#   .\run.ps1 spring-boot:run   -> starts the app (Ollama must be running)

$ErrorActionPreference = "Stop"
$mavenVersion = "3.9.9"
$tools = Join-Path $PSScriptRoot ".tools"
$mavenHome = Join-Path $tools "apache-maven-$mavenVersion"

if (-not (Test-Path $mavenHome)) {
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools "maven.zip"
    $url = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
    Write-Host "Downloading Maven $mavenVersion ..."
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
}

if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME is not set; relying on java from PATH."
}

& (Join-Path $mavenHome "bin\mvn.cmd") @args
