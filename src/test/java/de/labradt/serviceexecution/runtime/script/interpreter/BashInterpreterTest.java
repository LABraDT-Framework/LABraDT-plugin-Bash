package de.labradt.serviceexecution.runtime.script.interpreter;

import de.labradt.serviceexecution.runtime.script.interpreter.bash.BashInterpreter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for BashInterpreter with unified bash execution via WSL on Windows.
 * 
 * Architecture:
 * Unix/Linux: Native /bin/bash
 * Windows: WSL /bin/bash
 */
class BashInterpreterTest {

    private BashInterpreter interpreter;
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    @BeforeEach
    void setUp() {
        TestEnvironment.assumeBash();
        interpreter = new BashInterpreter();
    }

    @Nested
    @DisplayName("Bash Script Execution")
    class BashScriptTests {

        @Test
        @DisplayName("Execute bash arithmetic")
        void shouldExecuteArithmetic() throws Exception {
            String script = "echo $((2 + 3))";
            String result = interpreter.execute(script, Map.of());
            assertEquals("5", result);
        }

        @Test
        @DisplayName("Set and use environment variables")
        void shouldUseEnvironmentVariables() throws Exception {
            String script = "echo \"Machine: $MACHINE_NAME\"";
            Map<String, String> args = Map.of("MACHINE_NAME", "OSACA-Mill-01");
            String result = interpreter.execute(script, args);
            assertEquals("Machine: OSACA-Mill-01", result);
        }

        @Test
        @DisplayName("Execute multi-line bash script with conditionals")
        void shouldExecuteConditionalScript() throws Exception {
            String script = "if [ 5 -gt 3 ]; then\n    echo 'Five is greater than three'\nelse\n    echo 'Five is not greater than three'\nfi";
            String result = interpreter.execute(script, Map.of());
            assertEquals("Five is greater than three", result);
        }

        @Test
        @DisplayName("Execute bash script with multiple environment variables")
        void shouldHandleMultipleEnvironmentVariables() throws Exception {
            String script = "echo \"Temp: $TEMP_VAL, Pressure: $PRESSURE_VAL, RPM: $RPM_VAL\"\necho \"Sum: $(($TEMP_VAL + $PRESSURE_VAL + $RPM_VAL))\"";
            Map<String, String> args = new HashMap<>();
            args.put("TEMP_VAL", "25");
            args.put("PRESSURE_VAL", "5");
            args.put("RPM_VAL", "1000");
            
            String result = interpreter.execute(script, args);
            assertTrue(result.contains("Temp: 25, Pressure: 5, RPM: 1000"));
            assertTrue(result.contains("Sum: 1030"));
        }

        @Test
        @DisplayName("Execute bash script with string operations")
        void shouldHandleStringOperations() throws Exception {
            String script = "status='RUNNING'\necho \"Status: $status\"";
            String result = interpreter.execute(script, Map.of());
            assertTrue(result.contains("Status: RUNNING"));
        }
    }

    @Nested
    @DisplayName("Python Script Execution")
    class PythonScriptTests {

        @BeforeEach
        void requirePython3() {
            TestEnvironment.assumePython3();
        }

        @Test
        @DisplayName("Verify Python is installed via bash")
        void shouldVerifyPythonInstalled() throws Exception {
            String script = "python3 --version 2>&1";
            String result = interpreter.execute(script, Map.of());
            assertTrue(result.contains("Python"), "Expected Python version output, got: " + result);
        }

        @Test
        @DisplayName("Execute Python arithmetic")
        void shouldExecutePythonArithmetic() throws Exception {
            String script = "python3 -c \"print(2 + 3)\"";
            String result = interpreter.execute(script, Map.of());
            assertEquals("5", result);
        }

        @Test
        @DisplayName("Execute Python with environment variables")
        void shouldExecutePythonWithEnvironmentVariables() throws Exception {
            String script = "python3 -c \"import os; print(os.environ.get('MACHINE', 'N/A'))\"";
            Map<String, String> args = Map.of("MACHINE", "CNC-001");
            
            String result = interpreter.execute(script, args);
            assertEquals("CNC-001", result);
        }

        @Test
        @DisplayName("Execute Python environment variable comparison logic")
        void shouldExecutePythonConditionalLogic() throws Exception {
            String script = "python3 -c \"\nimport os\ntemp = float(os.environ.get('temperature', '0'))\nif temp > 80:\n    print('OVERHEAT')\nelse:\n    print('OK')\n\"";
            
            String resultOk = interpreter.execute(script, Map.of("temperature", "50"));
            assertEquals("OK", resultOk);
            
            String resultOverheat = interpreter.execute(script, Map.of("temperature", "95"));
            assertEquals("OVERHEAT", resultOverheat);
        }

        @Test
        @DisplayName("Execute Python with complex logic and f-strings")
        void shouldExecutePythonComplexLogic() throws Exception {
            String script = "python3 -c \"\nmachine = 'CNC-001'\nstatus = 'RUNNING'\nprint(f'Machine: {machine}, Status: {status}')\n\"";
            
            String result = interpreter.execute(script, Map.of());
            assertTrue(result.contains("Machine: CNC-001"));
            assertTrue(result.contains("Status: RUNNING"));
        }
    }

    @Nested
    @DisplayName("Resource File Execution")
    class ResourceFileTests {

        @BeforeEach
        void requirePython3() {
            TestEnvironment.assumePython3();
        }

        @Test
        @DisplayName("Execute sensor monitoring script - all sensors OK")
        void shouldExecuteSensorMonitorScript_AllOk() throws Exception {
            String script = "python3 " + getScriptPath("scripts/sensor_monitor.py");
            
            Map<String, String> args = new HashMap<>();
            args.put("temperature", "50");
            args.put("pressure", "5");
            args.put("rpm", "1500");
            
            String result = interpreter.execute(script, args);
            assertTrue(result.contains("Temperature: 50.0C - OK"));
            assertTrue(result.contains("Pressure: 5.0 bar - OK"));
            assertTrue(result.contains("RPM: 1500.0 - OK"));
            assertTrue(result.contains("ALL CLEAR"));
        }

        @Test
        @DisplayName("Execute sensor monitoring script - thresholds exceeded")
        void shouldExecuteSensorMonitorScript_ThresholdExceeded() throws Exception {
            String script = "python3 " + getScriptPath("scripts/sensor_monitor.py");
            
            Map<String, String> args = new HashMap<>();
            args.put("temperature", "90");
            args.put("pressure", "20");
            args.put("rpm", "1500");
            
            try {
                interpreter.execute(script, args);
                fail("Expected RuntimeException for threshold violations");
            } catch (RuntimeException e) {
                assertTrue(e.getMessage().contains("exit code"));
            }
        }
    }

    // Helper method to get the full path of a test resource script
    private String getScriptPath(String resourcePath) throws Exception {
        URI uri = this.getClass().getClassLoader().getResource(resourcePath).toURI();
        String absolutePath = Path.of(uri).toAbsolutePath().toString();
        
        // Convert to WSL path format on Windows
        if (IS_WINDOWS) {
            return convertToWslPath(absolutePath);
        }
        return absolutePath;
    }

    /**
     * Convert Windows path to WSL path format.
     * Example: D:\Users\test\file.sh -> /mnt/d/Users/test/file.sh
     */
    private String convertToWslPath(String windowsPath) {
        // Replace backslashes with forward slashes
        String unixPath = windowsPath.replace("\\", "/");
        
        // Handle drive letter: D:/... -> /mnt/d/...
        if (unixPath.length() >= 2 && Character.isLetter(unixPath.charAt(0)) && unixPath.charAt(1) == ':') {
            char drive = Character.toLowerCase(unixPath.charAt(0));
            unixPath = "/mnt/" + drive + unixPath.substring(2);
        }
        
        return unixPath;
    }

}
