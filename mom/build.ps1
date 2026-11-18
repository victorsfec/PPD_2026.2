param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $JdkHome) { throw 'Informe -JdkHome com a pasta do JDK 25 ou configure JAVA_HOME.' }
$compiler = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
$jar = Join-Path $JdkHome 'bin/jar.exe'
$classes = 'target/classes'
$testClasses = 'target/test-classes'
New-Item -ItemType Directory -Force $classes, $testClasses, 'lib' | Out-Null
$dependencies = @(
    @{ Path = 'org/apache/activemq/activemq-all/6.1.6'; File = 'activemq-all-6.1.6.jar' },
    @{ Path = 'org/apache/logging/log4j/log4j-api/2.24.3'; File = 'log4j-api-2.24.3.jar' },
    @{ Path = 'org/apache/logging/log4j/log4j-core/2.24.3'; File = 'log4j-core-2.24.3.jar' }
)
foreach ($dependency in $dependencies) {
    $destination = Join-Path 'lib' $dependency.File
    if (-not (Test-Path -LiteralPath $destination)) {
        Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/$($dependency.Path)/$($dependency.File)" -OutFile $destination
    }
}
$sources = @(Get-ChildItem src/main/java -Recurse -Filter *.java | ForEach-Object FullName)
& $compiler -encoding UTF-8 --release 25 -cp 'lib/*' -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação.' }
Copy-Item src/main/resources/* $classes -Recurse -Force
$tests = @(Get-ChildItem src/test/java -Recurse -Filter *.java | ForEach-Object FullName)
& $compiler -encoding UTF-8 --release 25 -cp "$classes;lib/*" -d $testClasses $tests
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes.' }
& $java -cp "$classes;$testClasses;lib/*" br.com.victorsfec.mom.AppTestMOM
if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
$entrypoints = @{
    client = 'client.MomClient'
    broker = 'broker.MomBroker'
    worker = 'worker.WordWorkerMOM'
    dashboard = 'dashboard.DashboardFrameMOM'
}
$classpath = ($dependencies | ForEach-Object { "../lib/$($_.File)" }) -join ' '
foreach ($kind in @('client', 'broker', 'worker', 'dashboard')) {
    $manifest = "Manifest-Version: 1.0`r`nMain-Class: br.com.victorsfec.mom.$($entrypoints[$kind])`r`nClass-Path: $classpath`r`n`r`n"
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'target/MANIFEST.MF'), $manifest, [System.Text.UTF8Encoding]::new($false))
    & $jar --create --file "target/mom-1.0-$kind.jar" --manifest target/MANIFEST.MF -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar JAR.' }
}
Write-Host 'Compilação, testes e quatro JARs concluídos. Mantenha lib/ ao lado de target/.'
