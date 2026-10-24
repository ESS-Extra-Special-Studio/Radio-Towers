# Rebuild RadioTowers and copy JAR to Forge test packs (C.Ideas + ESS Lastlight).
# Run from this folder: .\buildAndCopyToModpack.ps1
Set-Location $PSScriptRoot
.\gradlew build --no-daemon
if ($LASTEXITCODE -eq 0) {
    Write-Host "Done. JAR copied to C.Ideas + ESS Lastlight mods folders." -ForegroundColor Green
}
