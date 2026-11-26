# Builds GlassHome.apk using only the Android SDK command-line tools + Kotlin compiler
# (no Gradle / Android Studio needed).
#   Usage:  powershell -ExecutionPolicy Bypass -File build-apk.ps1 -KotlinHome C:\path\to\kotlinc
param(
    [string]$KotlinHome = $env:KOTLIN_HOME
)
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$bt = "$sdk\build-tools\35.0.0"
$androidJar = "$sdk\platforms\android-35\android.jar"
$out = "$root\build-manual"
$pkg = "com.charan.glasshome"

if (-not $KotlinHome -or -not (Test-Path "$KotlinHome\bin\kotlinc.bat")) { throw "Set -KotlinHome to the folder containing bin\kotlinc.bat" }
foreach ($f in "$bt\aapt2.exe", "$bt\d8.bat", "$bt\zipalign.exe", "$bt\apksigner.bat", $androidJar) {
    if (-not (Test-Path $f)) { throw "Missing $f" }
}

function Run($what, [scriptblock]$cmd) {
    Write-Host "==> $what"
    & $cmd
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit $LASTEXITCODE)" }
}

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

# 1. Resources + manifest
$manifest = (Get-Content "$root\app\src\main\AndroidManifest.xml" -Raw) -replace '<manifest ', "<manifest package=`"$pkg`" "
Set-Content -Path "$out\AndroidManifest.xml" -Value $manifest -Encoding UTF8
Run "Compiling resources" { & "$bt\aapt2.exe" compile --dir "$root\app\src\main\res" -o "$out\res.zip" }
Run "Linking resources" {
    & "$bt\aapt2.exe" link -o "$out\base.apk" -I $androidJar --manifest "$out\AndroidManifest.xml" `
        --java "$out\gen" --min-sdk-version 26 --target-sdk-version 34 `
        --version-code 4 --version-name 4.0 -A "$root\app\src\main\assets" "$out\res.zip"
}

# 2. Compile R.java, then the Kotlin sources
$rFiles = Get-ChildItem "$out\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Run "Compiling R.java" { & javac --release 11 -nowarn -cp $androidJar -d "$out\classes" $rFiles }
$ktFiles = Get-ChildItem "$root\app\src\main\java" -Recurse -Filter *.kt | ForEach-Object { $_.FullName }
Run "Compiling Kotlin" {
    & "$KotlinHome\bin\kotlinc.bat" $ktFiles -cp "$androidJar;$out\classes" -d "$out\classes" -jvm-target 11 -no-reflect -nowarn
}

# 3. Convert to Android bytecode (dex)
Run "Packaging classes" { & jar cf "$out\classes.jar" -C "$out\classes" . }
Run "Dexing" {
    & "$bt\d8.bat" --release --min-api 26 --lib $androidJar --output "$out\dex" "$out\classes.jar" "$KotlinHome\lib\kotlin-stdlib.jar"
}

# 4. Put classes.dex into the APK, align and sign
Copy-Item "$out\base.apk" "$out\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\unsigned.apk", [System.IO.Compression.ZipArchiveMode]::Update)
try {
    Get-ChildItem "$out\dex" -Filter *.dex | ForEach-Object {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $_.Name) | Out-Null
    }
} finally { $zip.Dispose() }

Run "Aligning" { & "$bt\zipalign.exe" -p -f 4 "$out\unsigned.apk" "$out\aligned.apk" }

$ks = "$env:USERPROFILE\.android\debug.keystore"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
    Run "Creating debug key" {
        & keytool -genkeypair -keystore $ks -storepass android -alias androiddebugkey -keypass android `
            -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    }
}
Run "Signing" {
    & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
        --out "$root\GlassHome.apk" "$out\aligned.apk"
}
Write-Host "`nDone: $root\GlassHome.apk" -ForegroundColor Green


