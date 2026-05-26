import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * APS de Sistemas Operacionais: benchmark de multiplicacao de matrizes usando
 * threads em uma unica JVM e processos em JVMs separadas.
 */
public class APSBenchmark {
    private static final String DEFAULT_DATA_DIR = "data";
    private static final String DEFAULT_RESULTS = "results/benchmark.csv";
    private static final String DEFAULT_SUMMARY = "results/summary.csv";
    private static final String DEFAULT_CHART = "docs/grafico-tempo.svg";

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        Map<String, String> options = parseOptions(args, 1);

        if ("suite".equals(command)) {
            runSuite(options);
        } else if ("threads".equals(command)) {
            runSingleMode("threads", options);
        } else if ("processes".equals(command) || "processos".equals(command)) {
            runSingleMode("processes", options);
        } else if ("prepare".equals(command)) {
            int size = getInt(options, "size", 1000);
            Path dataDir = Paths.get(getString(options, "data", DEFAULT_DATA_DIR));
            MatrixStore.prepare(size, dataDir);
            System.out.println("Matrizes prontas em: " + dataDir.toAbsolutePath());
        } else if ("worker".equals(command)) {
            runWorker(options);
        } else {
            System.err.println("Comando desconhecido: " + command);
            printUsage();
            System.exit(2);
        }
    }

    private static void runSuite(Map<String, String> options) throws Exception {
        int size = getInt(options, "size", 1000);
        int runs = getInt(options, "runs", 5);
        int warmups = getInt(options, "warmups", 1);
        int delayMs = getInt(options, "delay", 0);
        int[] units = parseUnits(getString(options, "units", "2,4,8"));
        Path dataDir = Paths.get(getString(options, "data", DEFAULT_DATA_DIR));
        Path csvPath = Paths.get(getString(options, "output", DEFAULT_RESULTS));
        Path summaryPath = Paths.get(getString(options, "summary", DEFAULT_SUMMARY));
        Path chartPath = Paths.get(getString(options, "chart", DEFAULT_CHART));

        MatrixStore.prepare(size, dataDir);

        List<BenchmarkResult> allResults = new ArrayList<BenchmarkResult>();
        for (int unitCount : units) {
            allResults.addAll(runRepeated("threads", size, unitCount, runs, warmups, delayMs, dataDir));
            allResults.addAll(runRepeated("processes", size, unitCount, runs, warmups, delayMs, dataDir));
        }

        markValidation(allResults);
        List<SummaryResult> summaries = summarize(allResults);
        writeBenchmarkCsv(csvPath, allResults);
        writeSummaryCsv(summaryPath, summaries);
        writeChart(chartPath, summaries);

        System.out.println();
        System.out.println("Resumo das medicoes oficiais (warmup desconsiderado):");
        for (SummaryResult summary : summaries) {
            System.out.printf(Locale.US,
                    "%-9s unidades=%d tempoMedio=%.3f ms memoriaPico=%.2f MB cpuPico=%.2f%% valido=%s%n",
                    summary.mode,
                    summary.units,
                    summary.averageDurationMs,
                    summary.maxHeapBytes / 1024.0 / 1024.0,
                    summary.maxCpuPercent,
                    summary.valid ? "sim" : "nao");
        }
        System.out.println();
        System.out.println("CSV detalhado: " + csvPath.toAbsolutePath());
        System.out.println("CSV resumido: " + summaryPath.toAbsolutePath());
        System.out.println("Grafico SVG: " + chartPath.toAbsolutePath());
    }

    private static void runSingleMode(String mode, Map<String, String> options) throws Exception {
        int size = getInt(options, "size", 1000);
        int units = getInt(options, "units", 4);
        int runs = getInt(options, "runs", 5);
        int warmups = getInt(options, "warmups", 1);
        int delayMs = getInt(options, "delay", 0);
        Path dataDir = Paths.get(getString(options, "data", DEFAULT_DATA_DIR));

        MatrixStore.prepare(size, dataDir);
        List<BenchmarkResult> results = runRepeated(mode, size, units, runs, warmups, delayMs, dataDir);
        for (BenchmarkResult result : results) {
            System.out.printf(Locale.US,
                    "%s run=%d warmup=%s unidades=%d tempo=%.3f ms checksum=%s memoriaPico=%.2f MB cpuPico=%.2f%%%n",
                    result.mode,
                    result.run,
                    result.warmup ? "sim" : "nao",
                    result.units,
                    result.durationMs,
                    result.signature(),
                    result.maxHeapBytes / 1024.0 / 1024.0,
                    result.maxCpuPercent);
        }
    }

    private static List<BenchmarkResult> runRepeated(
            String mode,
            int size,
            int units,
            int runs,
            int warmups,
            int delayMs,
            Path dataDir) throws Exception {
        List<BenchmarkResult> results = new ArrayList<BenchmarkResult>();

        int[] matrixA = null;
        int[] matrixBT = null;
        if ("threads".equals(mode)) {
            // No modo com threads, as matrizes ficam uma unica vez no heap compartilhado.
            matrixA = MatrixStore.readFullMatrix(MatrixStore.matrixAPath(dataDir, size), size);
            matrixBT = MatrixStore.readFullMatrix(MatrixStore.matrixBTPath(dataDir, size), size);
        }

        int totalRuns = warmups + runs;
        for (int i = 0; i < totalRuns; i++) {
            boolean warmup = i < warmups;
            int runNumber = warmup ? i + 1 : i - warmups + 1;
            BenchmarkResult result;
            if ("threads".equals(mode)) {
                result = runThreadsOnce(size, units, delayMs, matrixA, matrixBT);
            } else if ("processes".equals(mode)) {
                // No modo com processos, cada repeticao cria JVMs filhas independentes.
                result = runProcessesOnce(size, units, delayMs, dataDir);
            } else {
                throw new IllegalArgumentException("Modo invalido: " + mode);
            }
            result.mode = mode;
            result.units = units;
            result.run = runNumber;
            result.warmup = warmup;
            results.add(result);

            System.out.printf(Locale.US,
                    "%s unidades=%d run=%d/%d warmup=%s tempo=%.3f ms checksum=%s%n",
                    mode,
                    units,
                    i + 1,
                    totalRuns,
                    warmup ? "sim" : "nao",
                    result.durationMs,
                    result.signature());
        }
        return results;
    }

    private static BenchmarkResult runThreadsOnce(
            final int size,
            int units,
            int delayMs,
            final int[] matrixA,
            final int[] matrixBT) throws Exception {
        Monitor monitor = new Monitor();
        monitor.start();
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }

        long start = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(units);
        List<Callable<Checksum>> tasks = new ArrayList<Callable<Checksum>>();
        for (int unit = 0; unit < units; unit++) {
            final int startRow = unit * size / units;
            final int endRow = (unit + 1) * size / units;
            // Cada tarefa calcula um intervalo de linhas da matriz resultante.
            tasks.add(new Callable<Checksum>() {
                public Checksum call() {
                    return multiplyRows(size, matrixA, 0, matrixBT, startRow, endRow);
                }
            });
        }

        Checksum checksum = new Checksum();
        try {
            List<Future<Checksum>> futures = executor.invokeAll(tasks);
            for (Future<Checksum> future : futures) {
                checksum.combine(future.get());
            }
        } finally {
            executor.shutdownNow();
        }
        long end = System.nanoTime();
        monitor.stop();

        BenchmarkResult result = BenchmarkResult.fromChecksum(checksum);
        result.durationMs = nanosToMs(end - start);
        result.maxHeapBytes = monitor.getMaxHeapBytes();
        result.maxCpuPercent = monitor.getMaxCpuPercent();
        return result;
    }

    private static BenchmarkResult runProcessesOnce(int size, int units, int delayMs, Path dataDir) throws Exception {
        Path runRoot = Paths.get("results", "process-runs");
        Files.createDirectories(runRoot);
        Path runDir = Files.createTempDirectory(runRoot, "aps-processes-");
        List<Process> processes = new ArrayList<Process>();
        List<Path> outputFiles = new ArrayList<Path>();

        Monitor monitor = new Monitor();
        monitor.start();
        long start = System.nanoTime();
        try {
            for (int unit = 0; unit < units; unit++) {
                int startRow = unit * size / units;
                int endRow = (unit + 1) * size / units;
                Path outputFile = runDir.resolve("worker-" + unit + ".properties");
                Path logFile = runDir.resolve("worker-" + unit + ".log");
                outputFiles.add(outputFile);

                List<String> command = new ArrayList<String>();
                command.add(getJavaExecutable());
                command.add("-cp");
                command.add(System.getProperty("java.class.path"));
                command.add(APSBenchmark.class.getName());
                command.add("worker");
                command.add("--size");
                command.add(Integer.toString(size));
                command.add("--start");
                command.add(Integer.toString(startRow));
                command.add("--end");
                command.add(Integer.toString(endRow));
                command.add("--data");
                command.add(dataDir.toString());
                command.add("--out");
                command.add(outputFile.toString());
                command.add("--delay");
                command.add(Integer.toString(delayMs));

                // ProcessBuilder cria uma nova JVM para executar o mesmo programa no modo worker.
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                builder.redirectOutput(logFile.toFile());
                processes.add(builder.start());
            }

            for (Process process : processes) {
                int exit = process.waitFor();
                if (exit != 0) {
                    throw new IllegalStateException("Um processo filho terminou com codigo " + exit
                            + ". Verifique os logs em " + runDir.toAbsolutePath());
                }
            }
        } finally {
            monitor.stop();
        }
        long end = System.nanoTime();

        Checksum checksum = new Checksum();
        long maxHeapBytes = monitor.getMaxHeapBytes();
        double maxCpuPercent = monitor.getMaxCpuPercent();
        double maxWorkerDurationMs = 0.0;

        for (Path outputFile : outputFiles) {
            Properties properties = new Properties();
            FileInputStream input = new FileInputStream(outputFile.toFile());
            try {
                properties.load(input);
            } finally {
                input.close();
            }

            Checksum partial = new Checksum();
            partial.total = Long.parseLong(properties.getProperty("total"));
            partial.diagonal = Long.parseLong(properties.getProperty("diagonal"));
            partial.weighted = Long.parseLong(properties.getProperty("weighted"));
            checksum.combine(partial);

            maxHeapBytes += Long.parseLong(properties.getProperty("maxHeapBytes"));
            maxCpuPercent += Double.parseDouble(properties.getProperty("maxCpuPercent"));
            maxWorkerDurationMs = Math.max(maxWorkerDurationMs, Double.parseDouble(properties.getProperty("durationMs")));
        }

        BenchmarkResult result = BenchmarkResult.fromChecksum(checksum);
        double parentDurationMs = nanosToMs(end - start);
        result.durationMs = Math.max(maxWorkerDurationMs, parentDurationMs - delayMs);
        result.maxHeapBytes = maxHeapBytes;
        result.maxCpuPercent = maxCpuPercent;
        return result;
    }

    private static void runWorker(Map<String, String> options) throws Exception {
        int size = getRequiredInt(options, "size");
        int startRow = getRequiredInt(options, "start");
        int endRow = getRequiredInt(options, "end");
        int delayMs = getInt(options, "delay", 0);
        Path dataDir = Paths.get(getRequired(options, "data"));
        Path outputPath = Paths.get(getRequired(options, "out"));

        int[] matrixA = MatrixStore.readRows(MatrixStore.matrixAPath(dataDir, size), size, startRow, endRow);
        int[] matrixBT = MatrixStore.readFullMatrix(MatrixStore.matrixBTPath(dataDir, size), size);

        // O worker mede apenas o proprio processo filho e devolve o resultado parcial por arquivo.
        Monitor monitor = new Monitor();
        monitor.start();
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        long start = System.nanoTime();
        Checksum checksum = multiplyRows(size, matrixA, startRow, matrixBT, startRow, endRow);
        long end = System.nanoTime();
        monitor.stop();

        Properties properties = new Properties();
        properties.setProperty("startRow", Integer.toString(startRow));
        properties.setProperty("endRow", Integer.toString(endRow));
        properties.setProperty("durationMs", Double.toString(nanosToMs(end - start)));
        properties.setProperty("total", Long.toString(checksum.total));
        properties.setProperty("diagonal", Long.toString(checksum.diagonal));
        properties.setProperty("weighted", Long.toString(checksum.weighted));
        properties.setProperty("maxHeapBytes", Long.toString(monitor.getMaxHeapBytes()));
        properties.setProperty("maxCpuPercent", Double.toString(monitor.getMaxCpuPercent()));

        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        FileOutputStream output = new FileOutputStream(outputPath.toFile());
        try {
            properties.store(output, "Resultado parcial do processo filho");
        } finally {
            output.close();
        }
    }

    private static Checksum multiplyRows(
            int size,
            int[] matrixA,
            int firstRowInMatrixA,
            int[] matrixBT,
            int startRow,
            int endRow) {
        Checksum checksum = new Checksum();
        for (int row = startRow; row < endRow; row++) {
            int localRow = row - firstRowInMatrixA;
            int aBase = localRow * size;
            for (int col = 0; col < size; col++) {
                int btBase = col * size;
                long value = 0L;
                // Como B esta transposta, A[row][k] e BT[col][k] sao acessados sequencialmente.
                for (int k = 0; k < size; k++) {
                    value += (long) matrixA[aBase + k] * (long) matrixBT[btBase + k];
                }
                checksum.add(row, col, value);
            }
        }
        return checksum;
    }

    private static void markValidation(List<BenchmarkResult> results) {
        Map<Integer, Set<String>> signaturesByUnit = new LinkedHashMap<Integer, Set<String>>();
        for (BenchmarkResult result : results) {
            if (result.warmup) {
                continue;
            }
            Integer key = Integer.valueOf(result.units);
            Set<String> signatures = signaturesByUnit.get(key);
            if (signatures == null) {
                signatures = new LinkedHashSet<String>();
                signaturesByUnit.put(key, signatures);
            }
            signatures.add(result.signature());
        }

        for (BenchmarkResult result : results) {
            Set<String> signatures = signaturesByUnit.get(Integer.valueOf(result.units));
            result.valid = signatures != null && signatures.size() == 1;
        }
    }

    private static List<SummaryResult> summarize(List<BenchmarkResult> results) {
        Map<String, List<BenchmarkResult>> grouped = new LinkedHashMap<String, List<BenchmarkResult>>();
        for (BenchmarkResult result : results) {
            if (result.warmup) {
                continue;
            }
            String key = result.mode + ":" + result.units;
            List<BenchmarkResult> group = grouped.get(key);
            if (group == null) {
                group = new ArrayList<BenchmarkResult>();
                grouped.put(key, group);
            }
            group.add(result);
        }

        List<SummaryResult> summaries = new ArrayList<SummaryResult>();
        for (List<BenchmarkResult> group : grouped.values()) {
            SummaryResult summary = new SummaryResult();
            summary.mode = group.get(0).mode;
            summary.units = group.get(0).units;
            summary.valid = true;
            double durationSum = 0.0;
            long heapMax = 0L;
            double cpuMax = 0.0;
            for (BenchmarkResult result : group) {
                durationSum += result.durationMs;
                heapMax = Math.max(heapMax, result.maxHeapBytes);
                cpuMax = Math.max(cpuMax, result.maxCpuPercent);
                summary.valid = summary.valid && result.valid;
                summary.checksum = result.signature();
            }
            summary.averageDurationMs = durationSum / group.size();
            summary.maxHeapBytes = heapMax;
            summary.maxCpuPercent = cpuMax;
            summaries.add(summary);
        }
        return summaries;
    }

    private static void writeBenchmarkCsv(Path path, List<BenchmarkResult> results) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        PrintWriter writer = new PrintWriter(path.toFile(), "UTF-8");
        try {
            writer.println("mode,units,run,warmup,duration_ms,total,diagonal,weighted,checksum,max_heap_mb,max_cpu_percent,valid");
            for (BenchmarkResult result : results) {
                writer.printf(Locale.US,
                        "%s,%d,%d,%s,%.3f,%d,%d,%d,%s,%.3f,%.3f,%s%n",
                        result.mode,
                        result.units,
                        result.run,
                        Boolean.toString(result.warmup),
                        result.durationMs,
                        result.total,
                        result.diagonal,
                        result.weighted,
                        result.signature(),
                        result.maxHeapBytes / 1024.0 / 1024.0,
                        result.maxCpuPercent,
                        Boolean.toString(result.valid));
            }
        } finally {
            writer.close();
        }
    }

    private static void writeSummaryCsv(Path path, List<SummaryResult> summaries) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        PrintWriter writer = new PrintWriter(path.toFile(), "UTF-8");
        try {
            writer.println("mode,units,average_duration_ms,max_heap_mb,max_cpu_percent,checksum,valid");
            for (SummaryResult summary : summaries) {
                writer.printf(Locale.US,
                        "%s,%d,%.3f,%.3f,%.3f,%s,%s%n",
                        summary.mode,
                        summary.units,
                        summary.averageDurationMs,
                        summary.maxHeapBytes / 1024.0 / 1024.0,
                        summary.maxCpuPercent,
                        summary.checksum,
                        Boolean.toString(summary.valid));
            }
        } finally {
            writer.close();
        }
    }

    private static void writeChart(Path path, List<SummaryResult> summaries) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        double maxDuration = 0.0;
        for (SummaryResult summary : summaries) {
            maxDuration = Math.max(maxDuration, summary.averageDurationMs);
        }
        if (maxDuration <= 0.0) {
            maxDuration = 1.0;
        }

        int width = 900;
        int height = 520;
        int left = 80;
        int bottom = 440;
        int barWidth = 44;
        int groupSpacing = 110;
        int chartHeight = 330;

        PrintWriter writer = new PrintWriter(path.toFile(), "UTF-8");
        try {
            writer.println("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height + "\">");
            writer.println("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>");
            writer.println("<text x=\"450\" y=\"38\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"22\" font-weight=\"700\">Tempo medio por quantidade de unidades</text>");
            writer.println("<line x1=\"" + left + "\" y1=\"90\" x2=\"" + left + "\" y2=\"" + bottom + "\" stroke=\"#333\"/>");
            writer.println("<line x1=\"" + left + "\" y1=\"" + bottom + "\" x2=\"820\" y2=\"" + bottom + "\" stroke=\"#333\"/>");

            for (int i = 0; i <= 5; i++) {
                double value = maxDuration * i / 5.0;
                int y = bottom - (int) Math.round(chartHeight * i / 5.0);
                writer.println("<line x1=\"" + left + "\" y1=\"" + y + "\" x2=\"820\" y2=\"" + y + "\" stroke=\"#e0e0e0\"/>");
                writer.printf(Locale.US,
                        "<text x=\"70\" y=\"%d\" text-anchor=\"end\" font-family=\"Arial\" font-size=\"12\">%.0f</text>%n",
                        y + 4,
                        value);
            }

            Map<Integer, SummaryResult> threadByUnit = new LinkedHashMap<Integer, SummaryResult>();
            Map<Integer, SummaryResult> processByUnit = new LinkedHashMap<Integer, SummaryResult>();
            for (SummaryResult summary : summaries) {
                if ("threads".equals(summary.mode)) {
                    threadByUnit.put(Integer.valueOf(summary.units), summary);
                } else if ("processes".equals(summary.mode)) {
                    processByUnit.put(Integer.valueOf(summary.units), summary);
                }
            }

            int index = 0;
            for (Integer units : threadByUnit.keySet()) {
                int groupX = left + 70 + index * groupSpacing;
                SummaryResult thread = threadByUnit.get(units);
                SummaryResult process = processByUnit.get(units);
                drawBar(writer, groupX, bottom, chartHeight, barWidth, thread.averageDurationMs, maxDuration, "#2364aa", "Threads");
                if (process != null) {
                    drawBar(writer, groupX + barWidth + 10, bottom, chartHeight, barWidth, process.averageDurationMs, maxDuration, "#d1495b", "Processos");
                }
                writer.println("<text x=\"" + (groupX + barWidth + 5) + "\" y=\"475\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"14\">" + units + " unidades</text>");
                index++;
            }

            writer.println("<text x=\"450\" y=\"505\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"13\">Tempo em milissegundos; menor e melhor</text>");
            writer.println("<rect x=\"650\" y=\"58\" width=\"16\" height=\"16\" fill=\"#2364aa\"/><text x=\"674\" y=\"71\" font-family=\"Arial\" font-size=\"13\">Threads</text>");
            writer.println("<rect x=\"740\" y=\"58\" width=\"16\" height=\"16\" fill=\"#d1495b\"/><text x=\"764\" y=\"71\" font-family=\"Arial\" font-size=\"13\">Processos</text>");
            writer.println("</svg>");
        } finally {
            writer.close();
        }
    }

    private static void drawBar(
            PrintWriter writer,
            int x,
            int bottom,
            int chartHeight,
            int width,
            double value,
            double maxValue,
            String color,
            String label) {
        int height = (int) Math.round(chartHeight * value / maxValue);
        int y = bottom - height;
        writer.println("<rect x=\"" + x + "\" y=\"" + y + "\" width=\"" + width + "\" height=\"" + height + "\" fill=\"" + color + "\"/>");
        writer.printf(Locale.US,
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"11\" transform=\"rotate(-90 %d %d)\">%.0f</text>%n",
                x + width / 2,
                y - 6,
                x + width / 2,
                y - 6,
                value);
        writer.println("<title>" + label + ": " + String.format(Locale.US, "%.3f ms", value) + "</title>");
    }

    private static Map<String, String> parseOptions(String[] args, int startIndex) {
        Map<String, String> options = new LinkedHashMap<String, String>();
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key;
            String value;
            int equals = arg.indexOf('=');
            if (equals >= 0) {
                key = arg.substring(2, equals);
                value = arg.substring(equals + 1);
            } else {
                key = arg.substring(2);
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Opcao sem valor: " + arg);
                }
                value = args[++i];
            }
            options.put(key.toLowerCase(Locale.ROOT), value);
        }
        return options;
    }

    private static int[] parseUnits(String text) {
        String[] pieces = text.split(",");
        int[] units = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            units[i] = Integer.parseInt(pieces[i].trim());
            if (units[i] <= 0) {
                throw new IllegalArgumentException("Quantidade de unidades invalida: " + units[i]);
            }
        }
        return units;
    }

    private static String getString(Map<String, String> options, String key, String defaultValue) {
        String value = options.get(key);
        return value == null ? defaultValue : value;
    }

    private static String getRequired(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Opcao obrigatoria ausente: --" + key);
        }
        return value;
    }

    private static int getInt(Map<String, String> options, String key, int defaultValue) {
        String value = options.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static int getRequiredInt(Map<String, String> options, String key) {
        return Integer.parseInt(getRequired(options, key));
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        File java = new File(javaHome, "bin/java.exe");
        if (java.exists()) {
            return java.getAbsolutePath();
        }
        return new File(javaHome, "bin/java").getAbsolutePath();
    }

    private static void printUsage() {
        System.out.println("APSBenchmark - processos vs threads");
        System.out.println();
        System.out.println("Comandos:");
        System.out.println("  prepare   --size 1000 --data data");
        System.out.println("  threads   --size 1000 --units 4 --runs 5 --warmups 1");
        System.out.println("  processes --size 1000 --units 4 --runs 5 --warmups 1");
        System.out.println("  suite     --size 1000 --units 2,4,8 --runs 5 --warmups 1");
        System.out.println();
        System.out.println("Exemplo oficial:");
        System.out.println("  java -Xmx2g -cp bin APSBenchmark suite --size 1000 --units 2,4,8 --runs 5 --warmups 1");
    }

    private static final class MatrixStore {
        private MatrixStore() {
        }

        static Path matrixAPath(Path dataDir, int size) {
            return dataDir.resolve("matrix_A_" + size + ".bin");
        }

        static Path matrixBTPath(Path dataDir, int size) {
            return dataDir.resolve("matrix_BT_" + size + ".bin");
        }

        static void prepare(int size, Path dataDir) throws IOException {
            Files.createDirectories(dataDir);
            Path aPath = matrixAPath(dataDir, size);
            Path btPath = matrixBTPath(dataDir, size);
            long expectedBytes = (long) size * (long) size * 4L;
            if (Files.exists(aPath) && Files.exists(btPath)
                    && Files.size(aPath) == expectedBytes
                    && Files.size(btPath) == expectedBytes) {
                return;
            }

            System.out.println("Gerando matrizes deterministicas " + size + "x" + size + " em " + dataDir.toAbsolutePath());
            writeMatrixA(aPath, size);
            writeMatrixBT(btPath, size);
        }

        private static void writeMatrixA(Path path, int size) throws IOException {
            DataWriter writer = new DataWriter(path);
            try {
                for (int row = 0; row < size; row++) {
                    for (int col = 0; col < size; col++) {
                        writer.writeInt(aValue(row, col));
                    }
                }
            } finally {
                writer.close();
            }
        }

        private static void writeMatrixBT(Path path, int size) throws IOException {
            DataWriter writer = new DataWriter(path);
            try {
                for (int col = 0; col < size; col++) {
                    for (int row = 0; row < size; row++) {
                        writer.writeInt(bValue(row, col));
                    }
                }
            } finally {
                writer.close();
            }
        }

        static int[] readFullMatrix(Path path, int size) throws IOException {
            int[] matrix = new int[size * size];
            DataReader reader = new DataReader(path);
            try {
                for (int i = 0; i < matrix.length; i++) {
                    matrix[i] = reader.readInt();
                }
            } finally {
                reader.close();
            }
            return matrix;
        }

        static int[] readRows(Path path, int size, int startRow, int endRow) throws IOException {
            int rowCount = endRow - startRow;
            int[] rows = new int[rowCount * size];
            RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
            try {
                file.seek((long) startRow * (long) size * 4L);
                for (int i = 0; i < rows.length; i++) {
                    rows[i] = file.readInt();
                }
            } finally {
                file.close();
            }
            return rows;
        }

        private static int aValue(int row, int col) {
            return 1 + (int) ((row * 31L + col * 17L + 13L) % 10L);
        }

        private static int bValue(int row, int col) {
            return 1 + (int) ((row * 19L + col * 23L + 7L) % 10L);
        }
    }

    private static final class DataWriter {
        private final BufferedOutputStream output;

        DataWriter(Path path) throws IOException {
            this.output = new BufferedOutputStream(new FileOutputStream(path.toFile()), 1024 * 1024);
        }

        void writeInt(int value) throws IOException {
            output.write((value >>> 24) & 0xFF);
            output.write((value >>> 16) & 0xFF);
            output.write((value >>> 8) & 0xFF);
            output.write(value & 0xFF);
        }

        void close() throws IOException {
            output.close();
        }
    }

    private static final class DataReader {
        private final BufferedInputStream input;

        DataReader(Path path) throws IOException {
            this.input = new BufferedInputStream(new FileInputStream(path.toFile()), 1024 * 1024);
        }

        int readInt() throws IOException {
            int b1 = input.read();
            int b2 = input.read();
            int b3 = input.read();
            int b4 = input.read();
            if ((b1 | b2 | b3 | b4) < 0) {
                throw new IOException("Fim inesperado do arquivo de matriz");
            }
            return ((b1 & 0xFF) << 24)
                    | ((b2 & 0xFF) << 16)
                    | ((b3 & 0xFF) << 8)
                    | (b4 & 0xFF);
        }

        void close() throws IOException {
            input.close();
        }
    }

    private static final class Checksum {
        long total;
        long diagonal;
        long weighted;

        void add(int row, int col, long value) {
            total += value;
            if (row == col) {
                diagonal += value;
            }
            // Checksum ponderado ajuda a detectar diferencas de valor e de posicao.
            weighted += value * (31L * (row + 1L) + 17L * (col + 1L));
        }

        void combine(Checksum other) {
            total += other.total;
            diagonal += other.diagonal;
            weighted += other.weighted;
        }

        String signature() {
            return total + ":" + diagonal + ":" + weighted;
        }
    }

    private static final class Monitor implements Runnable {
        private volatile boolean running;
        private Thread thread;
        private long maxHeapBytes;
        private double maxCpuPercent;
        private final com.sun.management.OperatingSystemMXBean osBean;

        Monitor() {
            this.osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        }

        void start() {
            running = true;
            sample();
            thread = new Thread(this, "monitor-recursos");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() throws InterruptedException {
            running = false;
            if (thread != null) {
                thread.join();
            }
            sample();
        }

        public void run() {
            while (running) {
                sample();
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void sample() {
            Runtime runtime = Runtime.getRuntime();
            long usedHeap = runtime.totalMemory() - runtime.freeMemory();
            maxHeapBytes = Math.max(maxHeapBytes, usedHeap);

            double processCpuLoad = osBean.getProcessCpuLoad();
            if (processCpuLoad >= 0.0) {
                maxCpuPercent = Math.max(maxCpuPercent, processCpuLoad * 100.0);
            }
        }

        long getMaxHeapBytes() {
            return maxHeapBytes;
        }

        double getMaxCpuPercent() {
            return maxCpuPercent;
        }
    }

    private static final class BenchmarkResult {
        String mode;
        int units;
        int run;
        boolean warmup;
        double durationMs;
        long total;
        long diagonal;
        long weighted;
        long maxHeapBytes;
        double maxCpuPercent;
        boolean valid;

        static BenchmarkResult fromChecksum(Checksum checksum) {
            BenchmarkResult result = new BenchmarkResult();
            result.total = checksum.total;
            result.diagonal = checksum.diagonal;
            result.weighted = checksum.weighted;
            return result;
        }

        String signature() {
            return total + ":" + diagonal + ":" + weighted;
        }
    }

    private static final class SummaryResult {
        String mode;
        int units;
        double averageDurationMs;
        long maxHeapBytes;
        double maxCpuPercent;
        String checksum;
        boolean valid;
    }
}
