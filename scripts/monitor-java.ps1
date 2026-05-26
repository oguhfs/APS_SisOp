param(
    [int]$IntervalMs = 500,
    [string]$Output = "results\monitoramento.csv"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path (Split-Path $Output) | Out-Null
"timestamp,java_processes,total_working_set_mb,total_cpu_seconds" | Out-File -FilePath $Output -Encoding utf8

Write-Host "Monitorando processos java.exe. Pressione Ctrl+C para encerrar."
while ($true) {
    $processes = Get-Process java -ErrorAction SilentlyContinue
    $count = 0
    $memory = 0
    $cpu = 0

    foreach ($process in $processes) {
        $count += 1
        $memory += $process.WorkingSet64
        if ($process.CPU) {
            $cpu += $process.CPU
        }
    }

    $line = "{0},{1},{2:N3},{3:N3}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"), $count, ($memory / 1MB), $cpu
    $line | Out-File -FilePath $Output -Append -Encoding utf8
    Write-Host $line
    Start-Sleep -Milliseconds $IntervalMs
}
