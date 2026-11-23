# Bash

Bash script interpreter for the LABraDT Runtime (artifact `de.labradt:runtime-bash`). It runs
`SCRIPT` / `BASH` services: it writes the script to a temporary file, runs it with `/bin/bash` (on
Windows through WSL, `wsl /bin/bash /mnt/<drive>/...`), passes the service arguments as environment
variables (on Windows prepended to the script as `export` lines, because environment variables do
not cross `wsl.exe`) and returns the captured output. On Windows it expects the default `/mnt/<drive>`
automount root and a temp directory on a drive letter.

**Not sandboxed.** A Bash service runs as an ordinary shell process with the rights of the runtime's
user; only register scripts you trust.

## Known issues (from the code review, not fixed in the move)

- On Windows the argument **keys** are written into the script unvalidated (`export <key>=...`); a key
  containing shell syntax would run inside WSL. Values are escaped.
- The interpreter reads stdout, then stderr, then waits without a timeout; a script that fills the
  stderr pipe first can block its thread, and the process is not destroyed when the run is stopped.

## What is in here

| Path | What it is |
|---|---|
| `src/main/java/.../interpreter/bash/BashInterpreter.java` | `IScriptInterpreter` for `BASH` (native bash on Linux/macOS, WSL on Windows) |
| `src/test/` | `BashInterpreterTest` (bash scripts, a Python script run from bash, a resource script), `TestEnvironment` (bash / python3 checks), `scripts/sensor_monitor.py` |

The package is unchanged (`de.labradt.serviceexecution.runtime.script.interpreter.bash`), so the
runtime app finds the interpreter through its normal component scan once the jar is on its classpath.
The script host (`ScriptRuntime`, `IScriptInterpreter`) stays in the runtime core.

## Build

The module builds against the runtime core (`runtime-script`, `core-api` and the runtime parent pom)
from the local Maven repository. There is no package registry yet, so install the runtime first:

```bash
# in the runtime project
mvn install -DskipTests

# then here
mvn install
```

`mvn test` runs `BashInterpreterTest`. Its tests need bash (on Windows: WSL with a Linux
distribution) and, for the Python cases, `python3`; without them they are skipped with a reason.

## Use it with the runtime

Add the plugin to the dependencies of `service-manager-app/pom.xml` in the runtime project and rebuild
it:

```xml
<dependency>
    <groupId>de.labradt</groupId>
    <artifactId>runtime-bash</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

At startup the script host logs `Auto-registered interpreter: BASH → BashInterpreter`.
