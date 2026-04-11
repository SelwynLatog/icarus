# Powershell run script
param(
    [string]$target = "main"
)

$SRC     = "src"
$BIN     = "bin"
$LIB     = "src\lib"
$MAIN    = "Main"
$TEST    = "test.DBConnectionTest"

# Clean and prepare bin
if (Test-Path $BIN) { Remove-Item $BIN -Recurse -Force }
New-Item $BIN -ItemType Directory | Out-Null

# Collect all .java files
$javaFiles = Get-ChildItem -Path $SRC -Recurse -Filter "*.java" |
             Where-Object { $_.FullName -notlike "*\bin\*" } |
             ForEach-Object { $_.FullName }

if ($javaFiles.Count -eq 0) {
    Write-Host "No .java files found. Exiting." -ForegroundColor Red
    exit 1
}

# Compile
Write-Host "`nCompiling $($javaFiles.Count) file(s)..." -ForegroundColor Cyan

$classpath = "$LIB\*"
$compileArgs = @("-d", $BIN, "-cp", $classpath) + $javaFiles

javac @compileArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nCompilation failed." -ForegroundColor Red
    exit 1
}

Write-Host "Compilation successful.`n" -ForegroundColor Green

$runClasspath = "$BIN;$LIB\*"

if ($target -eq "test") {
    Write-Host "Running: $TEST`n" -ForegroundColor Yellow
    java -cp $runClasspath $TEST
} else {
    Write-Host "Running: $MAIN`n" -ForegroundColor Yellow
    java -cp $runClasspath $MAIN
}