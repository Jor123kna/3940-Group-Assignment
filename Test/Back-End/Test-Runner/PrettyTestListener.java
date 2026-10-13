
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class PrettyTestListener implements TestExecutionListener {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String SAVE = "\u001B7";
    private static final String RESTORE = "\u001B8";
    private static final String CLEAR_LINE = "\u001B[2K";
    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private static final int NAME_WIDTH = 64;
    private static final String LINE = "─".repeat(78);

    private final PrintStream out = new PrintStream(
        new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private final Object lock = new Object();
    private final int rows = Integer.getInteger("tests.rows", 0);
    private final int cols = Integer.getInteger("tests.cols", 100);
    private final boolean barEnabled = System.console() != null && rows > 3;
    private boolean regionOpen;
    private int spin;
    private ScheduledExecutorService ticker;
    private PrintStream realSystemOut;
    private PrintStream realSystemErr;

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> classStartUnits = new ConcurrentHashMap<>();
    private final List<String> failures = new ArrayList<>();

    private TestPlan plan;
    private long planStart;
    private volatile int totalUnits;
    private volatile int doneUnits;
    private volatile String currentClass = "";
    private volatile int passed;
    private volatile int failed;
    private volatile int skipped;

    public boolean allPassed() {
        return failed == 0 && passed + skipped > 0;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        plan = testPlan;
        planStart = System.nanoTime();
        int units = 0;
        for (TestIdentifier root : plan.getRoots()) {
            units += countUnits(root);
        }
        totalUnits = units;

        if (barEnabled) {
            realSystemOut = System.out;
            realSystemErr = System.err;
            System.setOut(locked(realSystemOut));
            System.setErr(locked(realSystemErr));
            openRegion();
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeRegion));
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "progress-bar");
                t.setDaemon(true);
                return t;
            });
            ticker.scheduleAtFixedRate(this::drawBar, 0, 100, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        startTimes.put(id.getUniqueId(), System.nanoTime());
        int depth = depth(id);
        if (!id.isContainer() || depth == 0) {
            return;
        }
        if (depth == 1) {
            currentClass = id.getDisplayName();
            classStartUnits.put(id.getUniqueId(), doneUnits);
            line("");
            line("  " + BOLD + CYAN + "▸ " + id.getDisplayName() + RESET);
        } else {
            line(indent(depth) + BOLD + name(id) + RESET);
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (id.isTest()) {
            skipped++;
            printResult(id, YELLOW + "○" + RESET, DIM + "skipped" + RESET);
            printDetail(id, YELLOW, reason);
        } else {
            skipped += countTests(id);
            line(indent(depth(id) + 1) + YELLOW + "○ skipped: " + reason + RESET);
        }
        doneUnits += countUnits(id);
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        String time = DIM + duration(id) + RESET;
        String reason = result.getThrowable().map(PrettyTestListener::message).orElse("");

        if (!id.isTest()) {
            if (result.getStatus() == TestExecutionResult.Status.FAILED && depth(id) > 0) {
                failed += Math.max(countTests(id), 1);
                line(indent(depth(id) + 1) + RED + "✘ could not run: " + reason + RESET);
                failures.add(path(id) + "\n      " + reason);
            } else if (result.getStatus() == TestExecutionResult.Status.ABORTED) {
                skipped += countTests(id);
                line(indent(depth(id) + 1) + YELLOW + "○ skipped: " + reason + RESET);
            }
            if (isTemplate(id)) {
                doneUnits++;
            }
            Integer classStart = classStartUnits.remove(id.getUniqueId());
            if (classStart != null) {
                doneUnits = Math.max(doneUnits, classStart + countUnits(id));
            }
            return;
        }

        switch (result.getStatus()) {
            case SUCCESSFUL:
                passed++;
                printResult(id, GREEN + "✔" + RESET, time);
                break;
            case ABORTED:
                skipped++;
                printResult(id, YELLOW + "○" + RESET, DIM + "skipped" + RESET);
                printDetail(id, YELLOW, reason);
                break;
            default:
                failed++;
                printResult(id, RED + "✘" + RESET, time);
                printDetail(id, RED, reason);
                String where = result.getThrowable().map(PrettyTestListener::location).orElse("");
                if (!where.isEmpty()) {
                    printDetail(id, DIM, "at " + where);
                }
                failures.add(path(id) + "\n      " + reason
                    + (where.isEmpty() ? "" : "\n      " + DIM + "at " + where + RESET));
        }

        if (isUnit(id)) {
            doneUnits++;
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (barEnabled) {
            ticker.shutdownNow();
            doneUnits = totalUnits;
            drawBar();
            closeRegion();
            System.setOut(realSystemOut);
            System.setErr(realSystemErr);
        }

        int total = passed + failed + skipped;
        String seconds = String.format("%.1f s", (System.nanoTime() - planStart) / 1e9);

        out.println();
        out.println("  " + DIM + LINE + RESET);

        if (!failures.isEmpty()) {
            out.println("  " + BOLD + RED + "Failed tests" + RESET);
            for (String f : failures) {
                out.println("   " + RED + "✘ " + RESET + f);
            }
            out.println("  " + DIM + LINE + RESET);
        }

        String badge = failed == 0
            ? GREEN + BOLD + " ✔ ALL TESTS PASSED " + RESET
            : RED + BOLD + " ✘ " + failed + (failed == 1 ? " TEST" : " TESTS") + " FAILED " + RESET;

        out.println("  " + badge
            + "   " + BOLD + total + RESET + " tests"
            + DIM + "  ·  " + RESET + GREEN + passed + " passed" + RESET
            + DIM + "  ·  " + RESET + (failed > 0 ? RED : DIM) + failed + " failed" + RESET
            + DIM + "  ·  " + RESET + (skipped > 0 ? YELLOW : DIM) + skipped + " skipped" + RESET
            + DIM + "  ·  " + seconds + RESET);
        out.println("  " + DIM + LINE + RESET);
    }

    private void line(String text) {
        synchronized (lock) {
            out.println(text);
        }
    }

    private void openRegion() {
        synchronized (lock) {
            out.print("\n" + SAVE + "\u001B[1;" + (rows - 1) + "r" + RESTORE + "\u001B[1A");
            out.flush();
            regionOpen = true;
        }
    }

    private void closeRegion() {
        synchronized (lock) {
            if (!regionOpen) {
                return;
            }
            out.print(SAVE + "\u001B[r" + "\u001B[" + rows + ";1H" + CLEAR_LINE + RESTORE);
            out.flush();
            regionOpen = false;
        }
    }

    private void drawBar() {
        synchronized (lock) {
            if (!regionOpen || totalUnits == 0) {
                return;
            }
            out.print(SAVE + "\u001B[" + rows + ";1H" + CLEAR_LINE + barText() + RESTORE);
            out.flush();
        }
    }

    private String barText() {
        int done = Math.min(doneUnits, totalUnits);
        boolean finished = done == totalUnits;
        int barWidth = Math.max(10, Math.min(40, cols - 60));
        int filled = done * barWidth / totalUnits;
        int percent = done * 100 / totalUnits;
        String color = failed > 0 ? RED : GREEN;
        String icon = finished ? color + "●" + RESET : CYAN + SPINNER[spin++ % SPINNER.length] + RESET;
        String seconds = String.format("%.1fs", (System.nanoTime() - planStart) / 1e9);
        int ran = passed + failed + skipped;

        String text = "  " + icon + " "
            + color + "█".repeat(filled) + RESET
            + DIM + "░".repeat(barWidth - filled) + RESET
            + BOLD + String.format(" %3d%%", percent) + RESET
            + DIM + "  " + ran + " tests" + RESET
            + (failed > 0 ? RED + "  " + failed + " failed" + RESET : "")
            + DIM + "  " + seconds
            + (currentClass.isEmpty() || finished ? "" : "  " + currentClass) + RESET;
        return fit(text, cols - 1);
    }

    private static String fit(String text, int width) {
        StringBuilder sb = new StringBuilder();
        int visible = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u001B') {
                int end = text.indexOf('m', i);
                sb.append(text, i, end + 1);
                i = end + 1;
                continue;
            }
            if (visible < width) {
                sb.append(c);
                visible++;
            }
            i++;
        }
        return sb.append(RESET).toString();
    }

    private PrintStream locked(PrintStream target) {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                synchronized (lock) {
                    target.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                synchronized (lock) {
                    target.write(b, off, len);
                    target.flush();
                }
            }

            @Override
            public void flush() {
                synchronized (lock) {
                    target.flush();
                }
            }
        }, true);
    }

    private void printResult(TestIdentifier id, String icon, String right) {
        String name = name(id);
        int pad = Math.max(1, NAME_WIDTH - indent(depth(id)).length() - name.length());
        line(indent(depth(id)) + icon + " " + name + " ".repeat(pad) + right);
    }

    private void printDetail(TestIdentifier id, String color, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        line(indent(depth(id)) + "    " + color + text + RESET);
    }

    private boolean isTemplate(TestIdentifier id) {
        return id.isContainer() && id.getSource().filter(s -> s instanceof MethodSource).isPresent();
    }

    private boolean isUnit(TestIdentifier id) {
        if (isTemplate(id)) {
            return true;
        }
        return id.isTest() && plan.getParent(id).map(p -> !isTemplate(p)).orElse(true);
    }

    private int countUnits(TestIdentifier id) {
        int units = isUnit(id) ? 1 : 0;
        for (TestIdentifier d : plan.getDescendants(id)) {
            if (isUnit(d)) {
                units++;
            }
        }
        return units;
    }

    private int depth(TestIdentifier id) {
        int depth = 0;
        Optional<TestIdentifier> parent = plan.getParent(id);
        while (parent.isPresent()) {
            depth++;
            parent = plan.getParent(parent.get());
        }
        return depth;
    }

    private long countTests(TestIdentifier id) {
        return plan.getDescendants(id).stream().filter(TestIdentifier::isTest).count();
    }

    private String duration(TestIdentifier id) {
        Long start = startTimes.remove(id.getUniqueId());
        if (start == null) {
            return "";
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        return ms < 1000 ? ms + " ms" : String.format("%.1f s", ms / 1000.0);
    }

    private String path(TestIdentifier id) {
        List<String> parts = new ArrayList<>();
        Optional<TestIdentifier> cur = Optional.of(id);
        while (cur.isPresent() && depth(cur.get()) > 0) {
            parts.add(0, depth(cur.get()) == 1 ? cur.get().getDisplayName() : name(cur.get()));
            cur = plan.getParent(cur.get());
        }
        return String.join(DIM + " › " + RESET, parts);
    }

    private static String indent(int depth) {
        return "  ".repeat(depth + 1);
    }

    private static String name(TestIdentifier id) {
        String name = id.getDisplayName();
        boolean isMethod = id.getSource().filter(s -> s instanceof MethodSource).isPresent();
        if (isMethod && name.endsWith(")")) {
            return humanize(name.substring(0, name.indexOf('(')));
        }
        return name.replaceFirst("^\\[\\d+\\]\\s*(\\w+=)?", "");
    }

    private static String humanize(String method) {
        String s = method
            .replaceAll("(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Za-z])|(?<=[A-Z])(?=[A-Z][a-z])", " ")
            .toLowerCase()
            .replaceAll("\\bajax\\b", "AJAX")
            .replaceAll("\\bjson\\b", "JSON")
            .replaceAll("\\bsql\\b", "SQL")
            .replaceAll("\\bdb\\b", "DB");
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String message(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = t.getClass().getSimpleName();
        }
        msg = msg.strip().replaceAll("\\s*\\R\\s*", " ");
        return msg.length() > 160 ? msg.substring(0, 157) + "..." : msg;
    }

    private static String location(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) {
            if (!e.getClassName().contains(".") && e.getClassName().endsWith("Test")
                    || e.getClassName().contains("Test$")) {
                return e.getClassName().replace('$', '.') + "." + e.getMethodName()
                    + " (" + e.getFileName() + ":" + e.getLineNumber() + ")";
            }
        }
        return "";
    }
}
