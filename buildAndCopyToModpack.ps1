# Rebuild RadioTowers and copy JAR to C.Ideas test modpack.
# Run from this folder: .\buildAndCopyToModpack.ps1
Set-Location $PSScriptRoot
.\gradlew build --no-daemon
if ($LASTEXITCODE -eq 0) {
    Write-Host "Done. JAR was copied to: $env:USERPROFILE\curseforge\minecraft\Instances\C.Ideas\mods" -ForegroundColor Green
}
