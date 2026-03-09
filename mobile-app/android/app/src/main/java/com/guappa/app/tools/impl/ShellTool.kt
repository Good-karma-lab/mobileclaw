package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShellTool : Tool {
    override val name = "shell"
    override val description = "Execute a sandboxed shell command. Only whitelisted commands are allowed: ls, cat, head, tail, wc, grep, find, du, date, uptime, whoami, df, ping"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "command": {
                    "type": "string",
                    "description": "Shell command to execute. Must start with an allowed command."
                }
            },
            "required": ["command"]
        }
    """.trimIndent())

    private val allowedCommands = setOf(
        "ls", "cat", "head", "tail", "wc", "grep", "find",
        "du", "date", "uptime", "whoami", "df", "ping"
    )

    private val dangerousPatterns = listOf(
        Regex("""rm\s+-[^\s]*r"""),       // rm -r, rm -rf, rm -fr, etc.
        Regex("""rm\s+-[^\s]*f"""),        // rm -f variants
        Regex("""\bmkfs\b"""),             // mkfs
        Regex("""\bdd\s+if="""),           // dd if=
        Regex("""\bchmod\s+777\b"""),      // chmod 777
        Regex("""\bsu\b"""),               // su
        Regex("""\bsudo\b"""),             // sudo
        Regex("""\breboot\b"""),           // reboot
        Regex("""\bshutdown\b"""),         // shutdown
        Regex("""\bformat\b"""),           // format
        Regex("""\bmount\b"""),            // mount
        Regex("""\bumount\b"""),           // umount
        Regex("""\bchown\b"""),            // chown
        Regex("""\bchroot\b"""),           // chroot
        Regex("""\binsmod\b"""),           // insmod
        Regex("""\brmmod\b"""),            // rmmod
        Regex("""\biptables\b"""),         // iptables
        Regex("""\b>\s*/"""),              // redirect to root paths
        Regex("""\|.*\bsh\b"""),           // pipe to sh
        Regex("""\|.*\bbash\b"""),         // pipe to bash
        Regex("""\$\("""),                 // command substitution
        Regex("""`"""),                    // backtick command substitution
        Regex("""\beval\b"""),             // eval
        Regex("""\bexec\b"""),             // exec
    )

    private val timeoutSeconds = 30L

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val command = params.optString("command", "").trim()
        if (command.isEmpty()) {
            return ToolResult.Error("Command is required.", "INVALID_PARAMS")
        }

        // Extract the base command (first word)
        val baseCommand = command.split(Regex("\\s+")).firstOrNull() ?: ""
        if (baseCommand !in allowedCommands) {
            return ToolResult.Error(
                "Command '$baseCommand' is not allowed. Allowed commands: ${allowedCommands.sorted().joinToString()}",
                "COMMAND_NOT_ALLOWED"
            )
        }

        // Check for dangerous patterns
        for (pattern in dangerousPatterns) {
            if (pattern.containsMatchIn(command)) {
                return ToolResult.Error(
                    "Command rejected: contains a dangerous pattern.",
                    "COMMAND_BLOCKED"
                )
            }
        }

        // Check for pipe chains that try to invoke disallowed commands
        if (command.contains("|")) {
            val pipeParts = command.split("|").map { it.trim() }
            for (part in pipeParts.drop(1)) {
                val pipedCmd = part.split(Regex("\\s+")).firstOrNull() ?: ""
                if (pipedCmd !in allowedCommands) {
                    return ToolResult.Error(
                        "Piped command '$pipedCmd' is not allowed. Only whitelisted commands can be piped.",
                        "COMMAND_NOT_ALLOWED"
                    )
                }
            }
        }

        return try {
            val result = withContext(Dispatchers.IO) {
                withTimeoutOrNull(timeoutSeconds * 1000L) {
                    executeCommand(command)
                }
            }

            if (result == null) {
                return ToolResult.Error(
                    "Command timed out after ${timeoutSeconds}s.",
                    "TIMEOUT"
                )
            }

            val (exitCode, stdout, stderr) = result

            val maxOutput = 50000
            val truncatedStdout = if (stdout.length > maxOutput) {
                stdout.take(maxOutput) + "\n...[truncated at $maxOutput chars]"
            } else stdout

            val data = JSONObject().apply {
                put("exit_code", exitCode)
                put("stdout_length", stdout.length)
                put("stderr_length", stderr.length)
                put("truncated", stdout.length > maxOutput)
                if (stderr.isNotEmpty()) put("stderr", stderr.take(5000))
            }

            if (exitCode == 0) {
                ToolResult.Success(
                    content = truncatedStdout.ifEmpty { "(no output)" },
                    data = data
                )
            } else {
                val errorInfo = if (stderr.isNotEmpty()) stderr.take(2000) else "Exit code: $exitCode"
                ToolResult.Error(
                    "Command failed (exit $exitCode): $errorInfo",
                    "COMMAND_FAILED"
                )
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to execute command: ${e.message}", "EXECUTION_ERROR")
        }
    }

    private fun executeCommand(command: String): Triple<Int, String, String> {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(false)
            .start()

        val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

        val stdout = stdoutReader.readText()
        val stderr = stderrReader.readText()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return Triple(-1, stdout, "Process killed after ${timeoutSeconds}s timeout")
        }

        return Triple(process.exitValue(), stdout, stderr)
    }
}
