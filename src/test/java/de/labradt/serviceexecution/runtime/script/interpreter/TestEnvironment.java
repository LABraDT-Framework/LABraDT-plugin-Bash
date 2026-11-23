package de.labradt.serviceexecution.runtime.script.interpreter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * bash and python3 checks for BashInterpreterTest. The tests stay in the normal run but are
 * skipped with a clear reason when bash (WSL on Windows) or python3 is missing.
 */
final class TestEnvironment {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private TestEnvironment() {
    }

    static void assumeBash() {
        assumeTrue(CACHE.computeIfAbsent("bash", k -> runsOk("echo ok")),
                (IS_WINDOWS ? "WSL with a default Linux distribution" : "/bin/bash") + " not available - skipped");
    }

    static void assumePython3() {
        assumeBash();
        assumeTrue(CACHE.computeIfAbsent("python3", k -> runsOk("python3 -c 'print(\"ok\")'")),
                "python3 not available in bash - skipped");
    }

    /** Runs a command the same way BashInterpreter does (wsl /bin/bash on Windows, /bin/bash elsewhere). */
    private static boolean runsOk(String bashCommand) {
        try {
            ProcessBuilder pb = IS_WINDOWS
                    ? new ProcessBuilder("wsl", "/bin/bash", "-c", bashCommand)
                    : new ProcessBuilder("/bin/bash", "-c", bashCommand);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.exitValue() == 0 && out.endsWith("ok");
        } catch (Exception e) {
            return false;
        }
    }
}
