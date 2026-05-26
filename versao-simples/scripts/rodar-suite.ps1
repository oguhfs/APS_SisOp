param(
    [int]$Tamanho = 1000,
    [int]$Repeticoes = 5
)

$ErrorActionPreference = "Stop"

$raiz = Resolve-Path (Join-Path $PSScriptRoot "..")
& "$PSScriptRoot\compilar.ps1"

Push-Location $raiz
try {
    java -Xmx2g -cp bin TrabalhoSimples suite $Tamanho $Repeticoes
} finally {
    Pop-Location
}
