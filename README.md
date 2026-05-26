# APS Sistemas Operacionais - Processos vs Threads

Trabalho de benchmark para comparar multiplicacao de matrizes quadradas usando:

- `threads`: varias threads dentro de uma unica JVM.
- `processes`: varias JVMs independentes criadas com `ProcessBuilder`.

O codigo foi escrito em Java 8 para ser compativel com ambientes de laboratorio mais antigos.

## Estrutura

- `src/APSBenchmark.java`: codigo-fonte principal.
- `scripts/compile.ps1`: compila o projeto para a pasta `bin`.
- `scripts/run-suite.ps1`: compila e executa a bateria oficial.
- `scripts/monitor-java.ps1`: monitora processos `java.exe` pelo PowerShell.
- `docs/relatorio-tecnico.md`: modelo do relatorio tecnico.
- `results/`: pasta gerada com CSVs de resultados.

## Requisitos

Instale um JDK 8 ou superior. Apenas o JRE nao e suficiente, pois o projeto precisa do `javac`.

Para conferir:

```powershell
javac -version
java -version
```

Se o `javac` nao aparecer, configure a variavel `JAVA_HOME` apontando para o JDK.
Os scripts tambem tentam localizar automaticamente JDKs do IntelliJ, incluindo o JBR em `C:\Program Files\JetBrains` e JDKs baixados em `%USERPROFILE%\.jdks`.

## Compilacao

```powershell
.\scripts\compile.ps1
```

Se o PowerShell bloquear scripts por politica de execucao, use:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\compile.ps1
```

## Teste rapido

Use uma matriz menor para validar se tudo esta funcionando antes da execucao oficial:

```powershell
java -Xmx1g -cp bin APSBenchmark suite --size 100 --units 2,4 --runs 1 --warmups 0
```

## Execucao oficial

O enunciado pede matrizes com dimensao minima de `1000 x 1000`, variando `2`, `4` e `8` unidades, com pelo menos `5` repeticoes. O comando abaixo gera os CSVs e o grafico:

```powershell
.\scripts\run-suite.ps1 -Size 1000 -Runs 5 -Warmups 1 -Units "2,4,8"
```

Se necessario, rode tambem com bypass:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-suite.ps1 -Size 1000 -Runs 5 -Warmups 1 -Units "2,4,8"
```

Arquivos gerados:

- `results\benchmark.csv`: medicoes detalhadas por execucao.
- `results\summary.csv`: medias finais para o relatorio.
- `docs\grafico-tempo.svg`: grafico de barras de tempo medio.

## Monitoramento

O proprio programa mede tempo, heap maximo e carga de CPU da JVM com `OperatingSystemMXBean`.

Para demonstrar no video usando PowerShell, abra um terminal separado:

```powershell
.\scripts\monitor-java.ps1
```

Depois execute o benchmark em outro terminal. Se quiser dar tempo de abrir o Monitor de Recursos ou ver o PowerShell capturando dados, use `-Delay 5000` no script de suite:

```powershell
.\scripts\run-suite.ps1 -Size 1000 -Runs 5 -Warmups 1 -Units "2,4,8" -Delay 5000
```

Para os tempos oficiais do relatorio, prefira `Delay = 0`.

## Como a validacao funciona

As matrizes `A` e `B` sao geradas de forma deterministica e gravadas em arquivos binarios na pasta `data`. A matriz `B` e armazenada transposta (`BT`) para melhorar o acesso em memoria durante a multiplicacao.

Cada execucao calcula:

- soma total dos valores da matriz resultante;
- soma da diagonal principal;
- checksum ponderado pela posicao de cada celula.

As versoes `threads` e `processes` sao consideradas corretas quando todas as assinaturas geradas sao identicas para o mesmo tamanho de matriz.

## Estrategia de processos

No modo `processes`, o programa principal cria varias JVMs com `ProcessBuilder`. Cada JVM recebe por argumento:

- dimensao da matriz;
- linha inicial;
- linha final;
- pasta dos dados;
- arquivo de saida do resultado parcial.

Cada processo filho carrega a matriz `BT` completa e apenas as linhas de `A` pelas quais e responsavel. Ao final, escreve um arquivo `.properties` com checksum, tempo, pico de heap e pico de CPU. O processo principal le esses arquivos e combina os resultados.

## Estrategia de threads

No modo `threads`, a JVM principal carrega `A` e `BT` uma vez no heap e usa `ExecutorService` para dividir os intervalos de linhas entre as threads. Como todas compartilham o mesmo heap, nao ha copia da matriz completa para cada unidade de execucao.
