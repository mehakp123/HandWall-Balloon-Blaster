$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Destination = Join-Path $Root "app/src/main/assets/hand_landmarker.task"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
Invoke-WebRequest -Uri "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task" -OutFile $Destination
if ((Get-Item $Destination).Length -lt 1000000) {
  throw "Downloaded model is unexpectedly small."
}
Write-Host "Downloaded MediaPipe hand model to $Destination"
