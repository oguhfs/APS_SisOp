# Relatorio Tecnico - Benchmarking: Processos vs Threads

## Integrantes

- Nome completo 1
- Nome completo 2
- Nome completo 3
- Nome completo 4

## 1. Objetivo

O objetivo deste trabalho foi comparar empiricamente o uso de processos e threads em uma tarefa computacional intensiva: a multiplicacao de duas matrizes quadradas de grande porte. A comparacao considerou tempo de execucao, consumo de CPU, consumo de memoria e nivel de isolamento.

## 2. Ambiente de teste

- Sistema operacional: Windows
- Linguagem: Java
- Versao do Java: preencher com `java -version`
- Processador: preencher com o modelo do computador usado
- Memoria RAM: preencher com a memoria total do computador usado
- Dimensao das matrizes: 1000 x 1000
- Unidades testadas: 2, 4 e 8 processos/threads
- Repeticoes consideradas: 5 execucoes por cenario
- Aquecimento: a primeira execucao pode ser desconsiderada

## 3. Implementacao

Foram implementadas duas versoes do programa no arquivo `src/APSBenchmark.java`.

Na versao com threads, a aplicacao carrega as matrizes uma unica vez no heap da JVM principal. Em seguida, usa `ExecutorService` com uma quantidade fixa de threads. Cada thread recebe um intervalo de linhas da matriz resultante e calcula apenas esse subconjunto.

Na versao com processos, a aplicacao principal cria novas JVMs por meio de `ProcessBuilder`. Cada processo filho recebe por argumento a dimensao da matriz, o intervalo de linhas, a pasta dos dados e o arquivo de saida. Cada processo carrega a matriz `BT` completa e apenas as linhas da matriz `A` pelas quais e responsavel.

As matrizes sao geradas de forma deterministica e gravadas em arquivos binarios. A matriz `B` e armazenada transposta, como `BT`, para melhorar o acesso sequencial no laco interno da multiplicacao.

## 4. Validacao

A validacao foi feita por meio de uma assinatura numerica da matriz resultante. Cada execucao calcula:

- soma total de todos os elementos;
- soma da diagonal principal;
- checksum ponderado pela posicao de cada celula.

As execucoes sao consideradas corretas quando as assinaturas das versoes com threads e processos sao identicas. Esse metodo evita gravar a matriz resultante completa em disco, mas ainda permite verificar se as duas abordagens produziram o mesmo resultado.

## 5. Coleta de dados

O tempo foi medido com `System.nanoTime()`. O consumo de memoria e CPU foi monitorado pelo programa usando `OperatingSystemMXBean`. Para demonstracao externa, tambem pode ser usado o script `scripts/monitor-java.ps1` ou o Monitor de Recursos do Windows.

Os resultados detalhados ficam em `results/benchmark.csv`, e o resumo das medias fica em `results/summary.csv`.

## 6. Resultados

Preencher esta tabela com os valores de `results/summary.csv` apos executar:

| Abordagem | Unidades | Tempo medio (ms) | CPU pico (%) | Memoria pico (MB) | Checksum valido |
|---|---:|---:|---:|---:|---|
| Processos | 2 | preencher | preencher | preencher | preencher |
| Threads | 2 | preencher | preencher | preencher | preencher |
| Processos | 4 | preencher | preencher | preencher | preencher |
| Threads | 4 | preencher | preencher | preencher | preencher |
| Processos | 8 | preencher | preencher | preencher | preencher |
| Threads | 8 | preencher | preencher | preencher | preencher |

O grafico de barras obrigatorio e gerado automaticamente em `docs/grafico-tempo.svg`.

## 7. Analise

### Qual abordagem foi mais rapida? Por que?

Preencher com base nos tempos medios obtidos. A tendencia esperada e que a versao com threads seja mais rapida, pois as threads compartilham a mesma JVM e o mesmo heap, evitando o custo de iniciar multiplas JVMs e de carregar novamente as matrizes em cada processo.

### Qual consumiu mais memoria? Por que?

Preencher com base no pico de memoria. A tendencia esperada e que a versao com processos consuma mais memoria, pois cada processo possui sua propria JVM, seu proprio heap e sua propria copia dos dados necessarios ao calculo.

### Qual apresentou maior isolamento?

A versao com processos apresenta maior isolamento. Cada processo tem espaco de memoria separado e uma falha em um processo filho tende a nao corromper diretamente a memoria dos demais. Threads, por outro lado, compartilham o mesmo heap e exigem mais cuidado com dados compartilhados.

### O ganho de desempenho foi linear ao aumentar as unidades?

Preencher com base no grafico. Em geral, o ganho nao costuma ser perfeitamente linear, pois ha custos de escalonamento, criacao de unidades, concorrencia por CPU, concorrencia por memoria/cache e, no caso dos processos, custo extra de inicializacao e carregamento de dados.

### Em quais cenarios processos podem ser preferiveis?

Processos podem ser preferiveis quando isolamento e tolerancia a falhas sao mais importantes que o menor consumo de memoria. Tambem sao adequados quando partes do sistema precisam executar com configuracoes diferentes, quando ha risco de falha independente ou quando se deseja separar responsabilidades de forma mais rigida.

## 8. Conclusao

Preencher apos executar os testes. A conclusao deve relacionar os resultados obtidos com a teoria: threads tendem a ter menor custo de criacao e compartilhamento de dados mais eficiente, enquanto processos oferecem maior isolamento ao custo de maior consumo de memoria e maior sobrecarga de criacao.
