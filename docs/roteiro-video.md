# Roteiro do Video - 3 a 5 minutos

## 1. Abertura

Apresentar o tema: comparacao entre processos e threads usando multiplicacao de matrizes em Java.

Falar rapidamente o objetivo:

- medir tempo de execucao;
- comparar uso de CPU;
- comparar uso de memoria;
- discutir isolamento.

## 2. Explicacao do codigo

Mostrar o arquivo `src/APSBenchmark.java`.

Pontos para explicar:

- comando `threads`: usa `ExecutorService`;
- comando `processes`: usa `ProcessBuilder` para criar JVMs separadas;
- cada unidade calcula um intervalo de linhas da matriz resultante;
- as matrizes sao deterministicas e ficam na pasta `data`;
- a matriz `B` e guardada transposta como `BT`;
- a validacao usa soma total, soma da diagonal e checksum ponderado.

## 3. Execucao das duas versoes

Mostrar primeiro uma execucao rapida:

```powershell
java -Xmx1g -cp bin APSBenchmark suite --size 100 --units 2,4 --runs 1 --warmups 0
```

Depois mostrar o comando oficial:

```powershell
.\scripts\run-suite.ps1 -Size 1000 -Runs 5 -Warmups 1 -Units "2,4,8"
```

Comentar que os resultados ficam em:

- `results\benchmark.csv`
- `results\summary.csv`
- `docs\grafico-tempo.svg`

## 4. Demonstracao do monitoramento

Abrir um segundo terminal e executar:

```powershell
.\scripts\monitor-java.ps1
```

Em outro terminal, executar:

```powershell
.\scripts\run-suite.ps1 -Size 1000 -Runs 1 -Warmups 0 -Units "4" -Delay 5000
```

Mostrar tambem o Monitor de Recursos do Windows, se possivel.

## 5. Analise dos resultados

Responder:

- qual abordagem foi mais rapida;
- qual consumiu mais memoria;
- qual teve maior isolamento;
- se o ganho ao aumentar unidades foi linear;
- em quais casos processos seriam preferiveis.

## 6. Fechamento

Concluir destacando a diferenca principal:

- threads tendem a ser mais leves e eficientes para compartilhar dados;
- processos tendem a consumir mais memoria, mas oferecem melhor isolamento.
