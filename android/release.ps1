<#
  Publie une version de l'appli Android :
    1. verifie que le numero de version est deja dans le code (app\build.gradle.kts)
    2. compile l'APK (gradlew assembleRelease)
    3. copie l'APK et version.json dans le depot de publication, puis commit et push
    4. commit, tag et push des sources (ce depot)

  Usage (PowerShell, depuis le dossier android) :
    .\release.ps1 -Version 0.1.0
    .\release.ps1 -Version 0.1.0 -SkipPush      # sans push (pour tester)

  ATTENTION : script non teste avant livraison. Verifie les chemins par defaut ci-dessous.
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$ReleasesDir = "C:\Users\lperr\Documents\charge-trotinette-releases",
    [string]$JavaHome = "C:\Program Files\Android\Android Studio\jbr",
    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"
$androidDir = $PSScriptRoot
$repoRoot = Split-Path -Parent $androidDir
$gradleFile = Join-Path $androidDir "app\build.gradle.kts"

function Run-Git {
    param([string]$Dir, [string[]]$GitArgs)
    & git -C $Dir @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') a echoue dans $Dir" }
}

# 1. Verifications
if (-not (Select-String -Path $gradleFile -Pattern "versionName = `"$Version`"" -SimpleMatch -Quiet)) {
    throw "versionName = `"$Version`" est introuvable dans app\build.gradle.kts : mets d'abord le numero a jour."
}
if (-not (Test-Path (Join-Path $repoRoot ".git"))) { throw "Depot des sources introuvable : $repoRoot" }
if (-not (Test-Path (Join-Path $ReleasesDir ".git"))) {
    throw "Depot de publication introuvable : $ReleasesDir (clone d'abord charge-trotinette-releases)."
}

# 2. Compilation
$env:JAVA_HOME = $JavaHome
Push-Location $androidDir
try {
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "La compilation a echoue." }
}
finally {
    Pop-Location
}
$apk = Join-Path $androidDir "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "APK introuvable : $apk" }

# 3. Depot de publication : APK + version.json (meme format que TopDriver)
Copy-Item $apk (Join-Path $ReleasesDir "ChargeTrotinette_latest.apk") -Force
$json = "{`n  `"latest`": `"v$Version`"`n}`n"
[System.IO.File]::WriteAllText((Join-Path $ReleasesDir "version.json"), $json, (New-Object System.Text.UTF8Encoding($false)))
Run-Git $ReleasesDir @("add", "-A")
Run-Git $ReleasesDir @("commit", "-m", "v$Version")
if (-not $SkipPush) { Run-Git $ReleasesDir @("push") }

# 4. Depot des sources : commit (s'il y a des changements), tag, push
Run-Git $repoRoot @("add", "-A")
& git -C $repoRoot diff --cached --quiet
if ($LASTEXITCODE -ne 0) { Run-Git $repoRoot @("commit", "-m", "v$Version") }
Run-Git $repoRoot @("tag", "-a", "v$Version", "-m", "v$Version")
if (-not $SkipPush) { Run-Git $repoRoot @("push", "origin", "main", "--follow-tags") }

Write-Host "Version v$Version publiee. APK : $ReleasesDir\ChargeTrotinette_latest.apk"
