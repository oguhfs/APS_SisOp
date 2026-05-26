import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Versao simples da APS: compara threads e processos na multiplicacao
 * de matrizes quadradas.
 */
public class TrabalhoSimples {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            mostrarAjuda();
            return;
        }

        String comando = args[0];
        if ("threads".equalsIgnoreCase(comando)) {
            int tamanho = lerInt(args, 1, 1000);
            int unidades = lerInt(args, 2, 4);
            Resultado resultado = executarThreads(tamanho, unidades);
            imprimirResultado(resultado);
        } else if ("processos".equalsIgnoreCase(comando)) {
            int tamanho = lerInt(args, 1, 1000);
            int unidades = lerInt(args, 2, 4);
            Resultado resultado = executarProcessos(tamanho, unidades);
            imprimirResultado(resultado);
        } else if ("suite".equalsIgnoreCase(comando)) {
            int tamanho = lerInt(args, 1, 1000);
            int repeticoes = lerInt(args, 2, 5);
            executarSuite(tamanho, repeticoes);
        } else if ("worker".equalsIgnoreCase(comando)) {
            int tamanho = Integer.parseInt(args[1]);
            int inicio = Integer.parseInt(args[2]);
            int fim = Integer.parseInt(args[3]);
            executarWorker(tamanho, inicio, fim);
        } else {
            mostrarAjuda();
        }
    }

    private static void executarSuite(int tamanho, int repeticoes) throws Exception {
        int[] unidades = {2, 4, 8};
        List<Resultado> resultados = new ArrayList<Resultado>();

        for (int unidade : unidades) {
            for (int repeticao = 1; repeticao <= repeticoes; repeticao++) {
                Resultado threads = executarThreads(tamanho, unidade);
                threads.repeticao = repeticao;
                resultados.add(threads);

                Resultado processos = executarProcessos(tamanho, unidade);
                processos.repeticao = repeticao;
                processos.valido = threads.assinatura().equals(processos.assinatura());
                resultados.add(processos);

                System.out.println("Unidades: " + unidade + " | repeticao: " + repeticao
                        + " | validacao: " + (processos.valido ? "OK" : "FALHOU"));
                imprimirResultado(threads);
                imprimirResultado(processos);
                System.out.println();
            }
        }

        escreverCsv(resultados);
        escreverResumo(resultados);
        escreverGrafico(resultados);

        System.out.println("Arquivos gerados em versao-simples/resultados");
    }

    private static Resultado executarThreads(int tamanho, int unidades) throws InterruptedException {
        long inicioTempo = System.nanoTime();

        int[][] matrizA = gerarMatrizA(tamanho);
        int[][] matrizB = gerarMatrizB(tamanho);
        long[][] resultado = new long[tamanho][tamanho];

        Thread[] threads = new Thread[unidades];
        final Resultado[] parciais = new Resultado[unidades];

        for (int unidade = 0; unidade < unidades; unidade++) {
            final int indice = unidade;
            final int linhaInicial = unidade * tamanho / unidades;
            final int linhaFinal = (unidade + 1) * tamanho / unidades;

            // Cada thread calcula um bloco de linhas da matriz final.
            threads[unidade] = new Thread(new Runnable() {
                public void run() {
                    parciais[indice] = multiplicarLinhas(matrizA, matrizB, resultado, linhaInicial, linhaFinal);
                }
            });
            threads[unidade].start();
        }

        Resultado total = new Resultado("threads", unidades);
        for (int i = 0; i < unidades; i++) {
            threads[i].join();
            total.somar(parciais[i]);
        }

        total.tempoMs = (System.nanoTime() - inicioTempo) / 1_000_000.0;
        total.memoriaMb = memoriaUsadaMb();
        total.cpuPico = cpuDoProcesso();
        total.valido = true;
        return total;
    }

    private static Resultado executarProcessos(int tamanho, int unidades) throws Exception {
        long inicioTempo = System.nanoTime();
        List<Process> processos = new ArrayList<Process>();
        List<BufferedReader> saidas = new ArrayList<BufferedReader>();

        for (int unidade = 0; unidade < unidades; unidade++) {
            int linhaInicial = unidade * tamanho / unidades;
            int linhaFinal = (unidade + 1) * tamanho / unidades;

            // ProcessBuilder cria outra JVM para calcular apenas uma parte das linhas.
            ProcessBuilder pb = new ProcessBuilder(
                    javaAtual(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    TrabalhoSimples.class.getName(),
                    "worker",
                    String.valueOf(tamanho),
                    String.valueOf(linhaInicial),
                    String.valueOf(linhaFinal));

            pb.redirectErrorStream(true);
            Process processo = pb.start();
            processos.add(processo);
            saidas.add(new BufferedReader(new InputStreamReader(processo.getInputStream())));
        }

        Resultado total = new Resultado("processos", unidades);
        for (int i = 0; i < processos.size(); i++) {
            Process processo = processos.get(i);
            String linha = saidas.get(i).readLine();
            int codigo = processo.waitFor();
            if (codigo != 0 || linha == null || !linha.startsWith("RESULTADO;")) {
                throw new IllegalStateException("Processo filho falhou. Saida: " + linha);
            }
            total.somar(Resultado.parse(linha));
        }

        total.tempoMs = (System.nanoTime() - inicioTempo) / 1_000_000.0;
        total.memoriaMb += memoriaUsadaMb();
        total.cpuPico += cpuDoProcesso();
        total.valido = true;
        return total;
    }

    private static void executarWorker(int tamanho, int inicio, int fim) {
        int[][] matrizA = gerarMatrizA(tamanho);
        int[][] matrizB = gerarMatrizB(tamanho);
        long[][] resultadoParcial = new long[fim - inicio][tamanho];

        Resultado parcial = multiplicarLinhas(matrizA, matrizB, resultadoParcial, inicio, fim);
        parcial.memoriaMb = memoriaUsadaMb();
        parcial.cpuPico = cpuDoProcesso();
        System.out.println(parcial.toLinhaProcesso());
    }

    private static Resultado multiplicarLinhas(
            int[][] matrizA,
            int[][] matrizB,
            long[][] resultado,
            int inicio,
            int fim) {
        int tamanho = matrizA.length;
        Resultado parcial = new Resultado("parcial", 1);

        for (int i = inicio; i < fim; i++) {
            for (int j = 0; j < tamanho; j++) {
                long valor = 0;
                for (int k = 0; k < tamanho; k++) {
                    valor += (long) matrizA[i][k] * matrizB[k][j];
                }

                int linhaResultado = resultado.length == tamanho ? i : i - inicio;
                resultado[linhaResultado][j] = valor;
                parcial.somaTotal += valor;

                if (i == j) {
                    parcial.somaDiagonal += valor;
                }

                // Checksum ponderado valida valor e posicao calculada.
                parcial.checksum += valor * (31L * (i + 1) + 17L * (j + 1));
            }
        }

        return parcial;
    }

    private static int[][] gerarMatrizA(int tamanho) {
        int[][] matriz = new int[tamanho][tamanho];
        for (int i = 0; i < tamanho; i++) {
            for (int j = 0; j < tamanho; j++) {
                matriz[i][j] = 1 + ((i + j) % 10);
            }
        }
        return matriz;
    }

    private static int[][] gerarMatrizB(int tamanho) {
        int[][] matriz = new int[tamanho][tamanho];
        for (int i = 0; i < tamanho; i++) {
            for (int j = 0; j < tamanho; j++) {
                matriz[i][j] = 1 + ((2 * i + j) % 10);
            }
        }
        return matriz;
    }

    private static void escreverCsv(List<Resultado> resultados) throws IOException {
        Path pasta = Paths.get("resultados");
        Files.createDirectories(pasta);

        PrintWriter writer = new PrintWriter(pasta.resolve("resultados.csv").toFile(), "UTF-8");
        writer.println("modo,unidades,repeticao,tempo_ms,memoria_mb,cpu_pico,soma_total,soma_diagonal,checksum,valido");
        for (Resultado r : resultados) {
            writer.printf(Locale.US, "%s,%d,%d,%.3f,%.3f,%.3f,%d,%d,%d,%s%n",
                    r.modo, r.unidades, r.repeticao, r.tempoMs, r.memoriaMb, r.cpuPico,
                    r.somaTotal, r.somaDiagonal, r.checksum, r.valido);
        }
        writer.close();
    }

    private static void escreverResumo(List<Resultado> resultados) throws IOException {
        Path pasta = Paths.get("resultados");
        Files.createDirectories(pasta);

        PrintWriter writer = new PrintWriter(pasta.resolve("resumo.csv").toFile(), "UTF-8");
        writer.println("modo,unidades,tempo_medio_ms,memoria_pico_mb,cpu_pico,validacao");

        String[] modos = {"threads", "processos"};
        int[] unidades = {2, 4, 8};

        for (String modo : modos) {
            for (int unidade : unidades) {
                int quantidade = 0;
                double somaTempo = 0;
                double memoriaPico = 0;
                double cpuPico = 0;
                boolean valido = true;

                for (Resultado r : resultados) {
                    if (r.modo.equals(modo) && r.unidades == unidade) {
                        quantidade++;
                        somaTempo += r.tempoMs;
                        memoriaPico = Math.max(memoriaPico, r.memoriaMb);
                        cpuPico = Math.max(cpuPico, r.cpuPico);
                        valido = valido && r.valido;
                    }
                }

                if (quantidade > 0) {
                    writer.printf(Locale.US, "%s,%d,%.3f,%.3f,%.3f,%s%n",
                            modo, unidade, somaTempo / quantidade, memoriaPico, cpuPico, valido);
                }
            }
        }

        writer.close();
    }

    private static void escreverGrafico(List<Resultado> resultados) throws IOException {
        Path pasta = Paths.get("resultados");
        Files.createDirectories(pasta);

        double maiorTempo = 1;
        for (Resultado r : resultados) {
            maiorTempo = Math.max(maiorTempo, media(resultados, r.modo, r.unidades));
        }

        PrintWriter writer = new PrintWriter(pasta.resolve("grafico-tempo.svg").toFile(), "UTF-8");
        writer.println("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"820\" height=\"460\">");
        writer.println("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
        writer.println("<text x=\"410\" y=\"35\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"22\">Tempo medio: processos vs threads</text>");
        writer.println("<line x1=\"70\" y1=\"390\" x2=\"760\" y2=\"390\" stroke=\"#333\"/>");
        writer.println("<line x1=\"70\" y1=\"70\" x2=\"70\" y2=\"390\" stroke=\"#333\"/>");

        int x = 120;
        int[] unidades = {2, 4, 8};
        for (int unidade : unidades) {
            double tempoThreads = media(resultados, "threads", unidade);
            double tempoProcessos = media(resultados, "processos", unidade);
            barra(writer, x, tempoThreads, maiorTempo, "#2f80ed");
            barra(writer, x + 50, tempoProcessos, maiorTempo, "#eb5757");
            writer.println("<text x=\"" + (x + 45) + "\" y=\"420\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"13\">" + unidade + " unidades</text>");
            x += 210;
        }

        writer.println("<rect x=\"575\" y=\"55\" width=\"14\" height=\"14\" fill=\"#2f80ed\"/><text x=\"595\" y=\"67\" font-family=\"Arial\" font-size=\"13\">Threads</text>");
        writer.println("<rect x=\"665\" y=\"55\" width=\"14\" height=\"14\" fill=\"#eb5757\"/><text x=\"685\" y=\"67\" font-family=\"Arial\" font-size=\"13\">Processos</text>");
        writer.println("</svg>");
        writer.close();
    }

    private static void barra(PrintWriter writer, int x, double tempo, double maiorTempo, String cor) {
        int altura = (int) Math.round((tempo / maiorTempo) * 290);
        int y = 390 - altura;
        writer.println("<rect x=\"" + x + "\" y=\"" + y + "\" width=\"38\" height=\"" + altura + "\" fill=\"" + cor + "\"/>");
        writer.printf(Locale.US, "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"11\">%.0f</text>%n",
                x + 19, y - 5, tempo);
    }

    private static double media(List<Resultado> resultados, String modo, int unidades) {
        double soma = 0;
        int quantidade = 0;
        for (Resultado r : resultados) {
            if (r.modo.equals(modo) && r.unidades == unidades) {
                soma += r.tempoMs;
                quantidade++;
            }
        }
        return quantidade == 0 ? 0 : soma / quantidade;
    }

    private static void imprimirResultado(Resultado r) {
        System.out.printf(Locale.US,
                "%s | unidades=%d | tempo=%.3f ms | memoria=%.3f MB | cpu=%.3f%% | checksum=%s%n",
                r.modo, r.unidades, r.tempoMs, r.memoriaMb, r.cpuPico, r.assinatura());
    }

    private static double memoriaUsadaMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0;
    }

    private static double cpuDoProcesso() {
        try {
            com.sun.management.OperatingSystemMXBean bean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double carga = bean.getProcessCpuLoad();
            return carga < 0 ? 0 : carga * 100.0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String javaAtual() {
        String javaHome = System.getProperty("java.home");
        File javaExe = new File(javaHome, "bin/java.exe");
        if (javaExe.exists()) {
            return javaExe.getAbsolutePath();
        }
        return new File(javaHome, "bin/java").getAbsolutePath();
    }

    private static int lerInt(String[] args, int posicao, int valorPadrao) {
        if (args.length <= posicao) {
            return valorPadrao;
        }
        return Integer.parseInt(args[posicao]);
    }

    private static void mostrarAjuda() {
        System.out.println("Uso:");
        System.out.println("  java -cp bin TrabalhoSimples threads 1000 4");
        System.out.println("  java -cp bin TrabalhoSimples processos 1000 4");
        System.out.println("  java -cp bin TrabalhoSimples suite 1000 5");
    }

    private static final class Resultado {
        String modo;
        int unidades;
        int repeticao;
        double tempoMs;
        double memoriaMb;
        double cpuPico;
        long somaTotal;
        long somaDiagonal;
        long checksum;
        boolean valido;

        Resultado(String modo, int unidades) {
            this.modo = modo;
            this.unidades = unidades;
        }

        void somar(Resultado outro) {
            this.somaTotal += outro.somaTotal;
            this.somaDiagonal += outro.somaDiagonal;
            this.checksum += outro.checksum;
            this.memoriaMb += outro.memoriaMb;
            this.cpuPico += outro.cpuPico;
        }

        String assinatura() {
            return somaTotal + ":" + somaDiagonal + ":" + checksum;
        }

        String toLinhaProcesso() {
            return String.format(Locale.US, "RESULTADO;%d;%d;%d;%.3f;%.3f",
                    somaTotal, somaDiagonal, checksum, memoriaMb, cpuPico);
        }

        static Resultado parse(String linha) {
            String[] partes = linha.split(";");
            Resultado r = new Resultado("parcial", 1);
            r.somaTotal = Long.parseLong(partes[1]);
            r.somaDiagonal = Long.parseLong(partes[2]);
            r.checksum = Long.parseLong(partes[3]);
            r.memoriaMb = Double.parseDouble(partes[4]);
            r.cpuPico = Double.parseDouble(partes[5]);
            return r;
        }
    }
}
