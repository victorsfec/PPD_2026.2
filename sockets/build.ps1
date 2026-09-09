param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $JdkHome) { throw 'Informe -JdkHome com a pasta do JDK 25 ou configure JAVA_HOME.' }
$compiler = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
$jar = Join-Path $JdkHome 'bin/jar.exe'
$classes = Join-Path $PSScriptRoot 'target/classes'
$testClasses = Join-Path $PSScriptRoot 'target/test-classes'
New-Item -ItemType Directory -Force $classes, $testClasses | Out-Null
$sources = @(Get-ChildItem src/main/java -Recurse -Filter *.java | ForEach-Object FullName)
& $compiler -encoding UTF-8 --release 25 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }
$tests = @(Get-ChildItem src/test/java -Recurse -Filter *.java | ForEach-Object FullName)
& $compiler -encoding UTF-8 --release 25 -cp $classes -d $testClasses $tests
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes.' }
& $java -cp "$classes;$testClasses" br.com.victorsfec.fanorona.AppTest
if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
foreach ($kind in @('server', 'client')) {
    $main = if ($kind -eq 'server') { 'server.FanoronaServer' } else { 'client.FanoronaClient' }
    & $jar --create --file (Join-Path $PSScriptRoot "target/sockets-1.0-$kind.jar") --main-class "br.com.victorsfec.fanorona.$main" -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar JAR.' }
}
Write-Host 'Compilação, testes e JARs concluídos.'
