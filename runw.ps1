param([string]$target = "main")

$SRC    = "src"
$BIN    = "bin"
$LIB    = "src\lib"
$NATIVE = "src\lib\native"
$MAIN   = "Main"
$TEST   = "test.DBConnectionTest"

if (Test-Path $BIN) { Remove-Item $BIN -Recurse -Force }
New-Item $BIN -ItemType Directory | Out-Null

$javaFiles = Get-ChildItem -Path $SRC -Recurse -Filter "*.java" |
             Where-Object { $_.FullName -notlike "*\bin\*" } |
             ForEach-Object { $_.FullName }

if ($javaFiles.Count -eq 0) {
    Write-Host "No .java files found." -ForegroundColor Red
    exit 1
}

Write-Host "`nCompiling $($javaFiles.Count) file(s)..." -ForegroundColor Cyan

javac -d $BIN -cp "$LIB\*" @javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nCompilation failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful.`n" -ForegroundColor Green

$cp = "$BIN;$LIB\*"
$nativePath = (Resolve-Path $NATIVE).Path

$nativeArg = "-Djava.library.path=$nativePath"
$moduleArg = "--enable-native-access=ALL-UNNAMED"

switch ($target) {
    "test"    { java $nativeArg $moduleArg -cp $cp $TEST }
    "barcode" { java $nativeArg $moduleArg -cp $cp "test.BarcodeTest" }
    "cv"      { java $nativeArg $moduleArg -cp $cp "test.CVTest" }
    default   { java $nativeArg $moduleArg -cp $cp $MAIN }
}