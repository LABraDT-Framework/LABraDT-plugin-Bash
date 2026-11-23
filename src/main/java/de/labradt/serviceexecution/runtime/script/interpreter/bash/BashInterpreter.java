package de.labradt.serviceexecution.runtime.script.interpreter.bash;

import de.labradt.serviceexecution.core.api.enums.TechnologyAdapter;

import de.labradt.serviceexecution.runtime.script.interpreter.IScriptInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BashInterpreter implements IScriptInterpreter {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    @Override
    public TechnologyAdapter getScriptType() {
        return TechnologyAdapter.BASH;
    }

    @Override
    public String execute(String scriptContent, Map<String, String> args) throws Exception {
        log.info("BashInterpreter: Executing script ({} lines, {} args) on OS: {}",
                scriptContent.split("\n").length, args.size(), OS_NAME);

        // Create a temporary script file
        Path tempScript = Files.createTempFile("bash_script_", ".sh");
        try {
            // For Windows/WSL: inject environment variables at the beginning of the script
            // because ProcessBuilder env vars don't transfer through wsl.exe
            String finalScript = scriptContent;
            if (IS_WINDOWS && !args.isEmpty()) {
                StringBuilder envVars = new StringBuilder();
                for (Map.Entry<String, String> entry : args.entrySet()) {
                    envVars.append("export ").append(entry.getKey()).append("='")
                           .append(entry.getValue().replace("'", "'\\''")).append("'\n");
                }
                finalScript = envVars + scriptContent;
                log.debug("BashInterpreter: Injected {} environment variables into WSL script", args.size());
            }

            // Write script content to temporary file
            Files.write(tempScript, finalScript.getBytes(StandardCharsets.UTF_8));
            
            // Make script executable on Unix-like systems
            if (!IS_WINDOWS) {
                tempScript.toFile().setExecutable(true);
            }

            // Build command based on OS
            // On Unix: /bin/bash script.sh
            // On Windows: wsl /bin/bash /mnt/d/path/to/script.sh
            List<String> command = buildCommand(tempScript);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            
            // On Unix, set environment variables from args in the process environment
            // On Windows, they are already injected into the script above
            if (!IS_WINDOWS) {
                Map<String, String> environment = processBuilder.environment();
                environment.putAll(args);
            }

            log.info("BashInterpreter: Starting process with command: {}", command);

            // Start the process
            Process process = processBuilder.start();

            // Capture stdout
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }

            // Capture stderr
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            // Wait for process to complete
            int exitCode = process.waitFor();

            String output = stdout.toString().trim();
            String errors = stderr.toString().trim();

            if (exitCode != 0) {
                String errorMessage = String.format(
                    "Bash script execution failed with exit code %d%s%s",
                    exitCode,
                    errors.isEmpty() ? "" : "\nStderr: " + errors,
                    output.isEmpty() ? "" : "\nStdout: " + output
                );
                log.error("BashInterpreter: {}", errorMessage);
                throw new RuntimeException(errorMessage);
            }

            if (!errors.isEmpty()) {
                log.warn("BashInterpreter: Stderr output: {}", errors);
                output = output.isEmpty() ? errors : output + "\n" + errors;
            }

            return output;

        } finally {
            // Clean up temporary file
            try {
                Files.delete(tempScript);
            } catch (Exception e) {
                log.warn("BashInterpreter: Failed to delete temporary script file", e);
            }
        }
    }

    // Helper method to build the command - uses WSL bash on Windows, native bash on Unix
    private List<String> buildCommand(Path tempScript) {
        List<String> command = new ArrayList<>();
        if (IS_WINDOWS) {
            // Convert Windows path to WSL path: D:\path\file -> /mnt/d/path/file
            String wslPath = convertToWslPath(tempScript.toAbsolutePath().toString());
            command.add("wsl");
            command.add("/bin/bash");
            command.add(wslPath);
        } else {
            // Native bash on Unix/Linux
            command.add("/bin/bash");
            command.add(tempScript.toAbsolutePath().toString());
        }
        return command;
    }

    // Convert Windows path to WSL path format
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
