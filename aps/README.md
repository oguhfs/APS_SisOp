# APS Sistemas Operacionais - Versao Simples

Esta e uma versao mais simples do trabalho de Processos vs Threads.

Ela compara a multiplicacao de matrizes quadradas em Java usando:

- `threads`: varias threads dentro da mesma JVM;
- `processos`: varias JVMs separadas criadas com `ProcessBuilder`.

## Como funciona

As matrizes sao geradas por formula dentro do codigo. Isso evita arquivos `.bin` e deixa a explicacao mais simples.

Cada thread ou processo recebe um intervalo de linhas da matriz resultante. Depois do calculo, o programa gera um checksum para validar se as duas abordagens chegaram ao mesmo resultado.

## Compilar

Na pasta principal do projeto, execute:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\versao-simples\scripts\compilar.ps1
```

## Rodar um teste rapido

```powershell
cd .\versao-simples
java -cp bin TrabalhoSimples suite 100 1
```

## Rodar o teste oficial

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\versao-simples\scripts\rodar-suite.ps1 -Tamanho 1000 -Repeticoes 5
```

O teste oficial executa automaticamente:

- 2 threads e 2 processos;
- 4 threads e 4 processos;
- 8 threads e 8 processos;
- 5 repeticoes para cada caso.

## Arquivos gerados

Depois da execucao, os resultados ficam em:

- `versao-simples\resultados\resultados.csv`
- `versao-simples\resultados\resumo.csv`
- `versao-simples\resultados\grafico-tempo.svg`

Use o `resumo.csv` para preencher a tabela do relatorio.

## Arquivos importantes

- `src\TrabalhoSimples.java`: codigo-fonte principal.
- `docs\relatorio-simples.md`: modelo de relatorio.
- `scripts\compilar.ps1`: compila o codigo.
- `scripts\rodar-suite.ps1`: executa a bateria de testes.
