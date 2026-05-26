# Relatorio Tecnico - Processos vs Threads

## Integrantes

- Gustavo Ferreira da Silva
- Gustavo Henrique Motta
- João Vitor Hess

## Objetivo

O objetivo do trabalho foi comparar o uso de processos e threads na multiplicacao de matrizes quadradas em Java. A comparacao considerou tempo de execucao, consumo de memoria, uso de CPU e nivel de isolamento.

## Ambiente de teste

- Sistema operacional: Windows
- Linguagem: Java
- Versao do Java: OpenJDK Runtime Environment JBR 21.0.7
- Arquitetura: AMD64
- Tamanho da matriz: 1000 x 1000
- Quantidades testadas: 2, 4 e 8 unidades
- Repeticoes: 5 por cenario

## Implementacao

O programa foi implementado no arquivo `src/TrabalhoSimples.java`.

Foram criadas duas versoes:

- versao com threads;
- versao com processos.

Na versao com threads, o programa cria varias threads dentro da mesma JVM. Todas as threads compartilham as matrizes na memoria e cada uma calcula um intervalo de linhas da matriz resultante.

Na versao com processos, o programa principal cria outras JVMs usando `ProcessBuilder`. Cada processo filho calcula um intervalo de linhas e devolve um resultado parcial para o processo principal.

As matrizes sao geradas por formula dentro do codigo. Assim, todas as execucoes usam os mesmos dados sem precisar salvar arquivos binarios.

## Validacao

A validacao foi feita por checksum. Cada execucao calcula:

- soma total dos elementos da matriz resultante;
- soma da diagonal principal;
- checksum ponderado pela posicao de cada valor.

As versoes com threads e processos foram consideradas corretas porque produziram a mesma assinatura em todos os testes:

`30250000000:30250000:726749375000000`

Todas as 15 comparacoes realizadas apresentaram validacao `OK`.

## Coleta de dados

O tempo foi medido pelo proprio programa usando `System.nanoTime()`. O consumo de memoria foi medido pela memoria em uso na JVM durante a execucao. O valor de CPU foi obtido pela API `OperatingSystemMXBean`.

Foram feitas 5 repeticoes para cada cenario:

- 2 threads e 2 processos;
- 4 threads e 4 processos;
- 8 threads e 8 processos.

## Resultados

| Abordagem | Unidades | Tempo medio (ms) | CPU pico (%) | Memoria pico (MB) | Valido |
|---|---:|---:|---:|---:|---|
| Threads | 2 | 609.948 | 16.294 | 36.084 | Sim |
| Processos | 2 | 1649.676 | 18.495 | 64.084 | Sim |
| Threads | 4 | 306.609 | 5.439 | 124.084 | Sim |
| Processos | 4 | 1667.038 | 27.988 | 173.876 | Sim |
| Threads | 8 | 229.422 | 6.453 | 143.584 | Sim |
| Processos | 8 | 2016.413 | 46.545 | 234.296 | Sim |

O grafico de barras foi gerado em `versao-simples/resultados/grafico-tempo.svg`.

## Analise

### Qual abordagem foi mais rapida?

A abordagem com threads foi mais rapida em todos os cenarios. Com 2 unidades, as threads tiveram media de 609.948 ms, enquanto os processos tiveram media de 1649.676 ms. Com 4 unidades, as threads ficaram em 306.609 ms, contra 1667.038 ms dos processos. Com 8 unidades, as threads ficaram em 229.422 ms, contra 2016.413 ms dos processos.

Isso aconteceu porque as threads compartilham a mesma JVM e o mesmo espaco de memoria. Ja na versao com processos, o programa precisa criar JVMs separadas, o que gera um custo maior de inicializacao e maior consumo de recursos.

### Qual consumiu mais memoria?

A abordagem com processos consumiu mais memoria em todos os cenarios. Com 2 unidades, processos chegaram a 64.084 MB, enquanto threads chegaram a 36.084 MB. Com 4 unidades, processos chegaram a 173.876 MB, enquanto threads chegaram a 124.084 MB. Com 8 unidades, processos chegaram a 234.296 MB, enquanto threads chegaram a 143.584 MB.

Esse resultado era esperado, pois cada processo possui sua propria JVM e seu proprio espaco de memoria. As threads, por outro lado, compartilham a memoria da mesma JVM.

### Qual apresentou maior isolamento?

A versao com processos apresentou maior isolamento. Cada processo executa em uma JVM separada e possui espaco de memoria independente. Se um processo falhar, a falha tende a ficar isolada naquele processo.

Na versao com threads, todas as threads compartilham o mesmo heap da JVM. Isso torna a comunicacao mais simples e eficiente, mas reduz o isolamento.

### O ganho de desempenho foi linear?

O ganho de desempenho nao foi linear. Na versao com threads, aumentar de 2 para 4 unidades reduziu o tempo medio de 609.948 ms para 306.609 ms, um ganho proximo do ideal. Porem, ao aumentar de 4 para 8 unidades, o tempo caiu apenas para 229.422 ms, mostrando que o ganho diminuiu.

Na versao com processos, o desempenho nao melhorou ao aumentar as unidades. Com 2 processos, o tempo medio foi 1649.676 ms. Com 4 processos, subiu para 1667.038 ms. Com 8 processos, subiu para 2016.413 ms. Isso indica que o custo de criar e executar muitas JVMs foi maior que o ganho obtido pela divisao do trabalho.

### Quando processos podem ser preferiveis?

Processos podem ser preferiveis quando o isolamento e mais importante que o desempenho. Eles sao uteis em sistemas nos quais uma falha em uma parte nao deve afetar diretamente as outras partes.

Tambem podem ser usados quando cada parte do sistema precisa executar com configuracoes diferentes, permissoes diferentes ou maior independencia.

## Conclusao

Com os testes realizados, foi possivel observar que threads foram mais eficientes para a multiplicacao de matrizes neste ambiente. Elas apresentaram menor tempo medio de execucao e menor consumo de memoria.

Os processos, apesar de mais lentos e mais custosos em memoria, apresentaram maior isolamento. Portanto, a melhor abordagem depende do objetivo: threads sao mais indicadas quando se busca desempenho e compartilhamento de memoria; processos sao mais indicados quando se busca isolamento e independencia entre as unidades de execucao.
