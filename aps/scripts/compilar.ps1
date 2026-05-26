$ErrorActionPreference = "Stop"

function Encontrar-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }

    $possiveis = @(
        "C:\Program Files\JetBrains\IntelliJ IDEA*\jbr",
        "$env:USERPROFILE\.jdks\*",
        "C:\Program Files\Java\*"
    )

    foreach ($padrao in $possiveis) {
        $pastas = Get-ChildItem -Path $padrao -Directory -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending
        foreach ($pasta in $pastas) {
            if (Test-Path (Join-Path $pasta.FullName "bin\javac.exe")) {
                return $pasta.FullName
            }
        }
    }

    throw "JDK nao encontrado. Configure JAVA_HOME ou instale um JDK."
}

$raiz = Resolve-Path (Join-Path $PSScriptRoot "..")
$jdk = Encontrar-Jdk
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

Write-Host "Usando JAVA_HOME=$jdk"
New-Item -ItemType Directory -Force -Path (Join-Path $raiz "bin") | Out-Null

javac -encoding UTF-8 -d (Join-Path $raiz "bin") (Join-Path $raiz "src\TrabalhoSimples.java")
Write-Host "Compilado em versao-simples\bin"
