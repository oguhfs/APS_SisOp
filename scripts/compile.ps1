param(
    [string]$SourceDir = "src",
    [string]$OutputDir = "bin"
)

$ErrorActionPreference = "Stop"

function Resolve-JdkHome {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\javac.exe"
        if (Test-Path $candidate) {
            return $env:JAVA_HOME
        }
    }

    $command = Get-Command javac -ErrorAction SilentlyContinue
    if ($command) {
        return (Split-Path (Split-Path $command.Source -Parent) -Parent)
    }

    $roots = @(
        "C:\Program Files\JetBrains\IntelliJ IDEA*\jbr",
        "$env:USERPROFILE\.jdks\*",
        "C:\Program Files\Java\*",
        "C:\Program Files\Eclipse Adoptium\*",
        "C:\Program Files\Microsoft\jdk-*",
        "C:\Program Files\BellSoft\*",
        "C:\Program Files\Zulu\*"
    )

    foreach ($root in $roots) {
        $matches = Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending
        foreach ($match in $matches) {
            $candidate = Join-Path $match.FullName "bin\javac.exe"
            if (Test-Path $candidate) {
                return $match.FullName
            }
        }
    }

    return $null
}

$jdkHome = Resolve-JdkHome
if (-not $jdkHome) {
    throw "javac nao encontrado. Instale um JDK 8+ ou configure JAVA_HOME apontando para o JDK."
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"
$javac = Join-Path $jdkHome "bin\javac.exe"

Write-Host "Usando JAVA_HOME=$jdkHome"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$sources = Get-ChildItem -Path $SourceDir -Filter *.java -Recurse | ForEach-Object { $_.FullName }

if (-not $sources) {
    throw "Nenhum arquivo .java encontrado em $SourceDir."
}

& $javac -encoding UTF-8 -d $OutputDir $sources

if ($LASTEXITCODE -ne 0) {
    throw "Falha na compilacao."
}

Write-Host "Compilacao concluida em $OutputDir"
