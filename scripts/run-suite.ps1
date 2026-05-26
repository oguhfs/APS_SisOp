param(
    [int]$Size = 1000,
    [int]$Runs = 5,
    [int]$Warmups = 1,
    [string]$Units = "2,4,8",
    [int]$Delay = 0,
    [string]$JavaOptions = "-Xmx2g"
)

$ErrorActionPreference = "Stop"

& "$PSScriptRoot\compile.ps1"

$javaArgs = @()
if ($JavaOptions.Trim().Length -gt 0) {
    $javaArgs += $JavaOptions.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
}

$javaArgs += @(
    "-cp", "bin",
    "APSBenchmark",
    "suite",
    "--size", "$Size",
    "--runs", "$Runs",
    "--warmups", "$Warmups",
    "--units", "$Units",
    "--delay", "$Delay",
    "--data", "data",
    "--output", "results\benchmark.csv",
    "--summary", "results\summary.csv",
    "--chart", "docs\grafico-tempo.svg"
)

& java @javaArgs
