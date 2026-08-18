package com.wificonfig.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Root Shell 执行工具
 * 通过 `su` 获取 Root 权限后执行底层 Shell 命令
 */
object RootShell {

    private const val SU_BINARY = "su"
    private const val EXIT = "exit\n"
    private const val EXIT_CODE_MARKER = "EXIT_CODE:%d"

    /**
     * 单次 Root 命令执行结果
     */
    data class CommandResult(
        val success: Boolean,
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    /**
     * 检查设备是否拥有 Root 权限（并尝试弹出授权窗口）
     */
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        val result = execute("id")
        result.success && result.stdout.contains("uid=0")
    }

    /**
     * 执行单条命令
     */
    suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        executeMultiple(listOf(command))
    }

    /**
     * 每条命令单独启动一个 su 会话执行，并用 waitFor(timeout) 防止任一条命令阻塞导致 UI 卡死。
     *
     * 虽然"单命令单 su"比"一次 su 会话跑多条"慢（大约 40~80 ms / 条，40 条 ≈ 2~3 秒），
     * 但能保证：
     *   ① 某条命令 hang 时（例如 cmd connectivity network-agent suspend 在 MIUI 某些版本上永不返回）
     *     最多等 5 秒就会被 destroy 并返回超时，不会整体卡读条；
     *   ② 命令边界绝对清晰，不会因为 stdout/stderr 定界符匹配失败而永远 readLine 阻塞；
     *   ③ 每条的 exit / stdout / stderr 独立记录，排查 MIUI 兼容问题更直观。
     */
    suspend fun executeDiagnosed(commands: List<String>): Pair<CommandResult, List<com.wificonfig.app.data.CommandDiagnostic>> =
        withContext(Dispatchers.IO) {
            val diagnostics = ArrayList<com.wificonfig.app.data.CommandDiagnostic>(commands.size)
            var overallExit = 0
            val globalStdout = StringBuilder()
            val globalStderr = StringBuilder()

            // 为了减少 su 进程启动开销，对于包含 `|| true` 的容错命令单独设更短的超时
            val CMD_TIMEOUT_SOFT_SEC = 3L   // 命令自带容错（末尾有 2>/dev/null || true）：3 秒
            val CMD_TIMEOUT_HARD_SEC = 5L   // 关键命令（ip / iptables）：5 秒

            commands.forEachIndexed { idx, rawCmd ->
                val cmdIdx = idx + 1
                val softTimeout = rawCmd.trimEnd().endsWith("|| true")
                val timeout = if (softTimeout) CMD_TIMEOUT_SOFT_SEC else CMD_TIMEOUT_HARD_SEC

                // 单命令单独包装成 shell：先 sh -c 再获取退出码
                // 同时给 stdout 前加一个 marker 保证即使无输出 stdout 流也能关闭
                val shCmd = listOf(
                    "exec 2>/dev/null",   // 屏蔽 su 自己的提示
                    rawCmd
                ).joinToString(" ; ")

                var process: Process? = null
                var exitCode: Int
                var stdoutText = ""
                var stderrText = ""
                try {
                    process = ProcessBuilder(SU_BINARY, "-c", rawCmd)
                        .redirectErrorStream(false)
                        .start()
                    val finished = process.waitFor(timeout, TimeUnit.SECONDS)
                    if (!finished) {
                        // 超时了：强制 kill，再拿退出码（拿不到就赋值 -3）
                        process.destroyForcibly()
                        process.waitFor(1, TimeUnit.SECONDS)
                        exitCode = -3
                        stderrText = "命令执行超时（${timeout}s 未返回，已强制终止）。如果这是一条容错命令（末尾 || true），通常可忽略。"
                    } else {
                        exitCode = process.exitValue()
                    }
                    // 读 stdout（不论正常结束还是超时，只要缓冲区里有就拿出来）
                    stdoutText = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                    stderrText = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                        .let { if (exitCode == -3 && it.isEmpty()) stderrText else it }
                } catch (e: IOException) {
                    exitCode = -1
                    stderrText = (e.message ?: "IOException") + "（无法启动 su，请确认 Magisk 已授予本应用永久 Root）"
                } catch (t: Throwable) {
                    exitCode = -2
                    stderrText = "${t.javaClass.simpleName}: ${t.message ?: "Unknown error"}"
                } finally {
                    runCatching { process?.destroy() }
                }

                val diag = com.wificonfig.app.data.CommandDiagnostic(
                    index = cmdIdx,
                    command = rawCmd.take(180),
                    exitCode = exitCode,
                    stdout = stdoutText.take(4000),
                    stderr = stderrText.take(4000)
                )
                diagnostics.add(diag)
                if (stdoutText.isNotEmpty()) globalStdout.appendLine(stdoutText)
                if (stderrText.isNotEmpty()) globalStderr.appendLine(stderrText)
                // 容错命令（|| true）超时或失败不影响整体 success（因为用户本来就允许失败）
                if (!diag.ok && !softTimeout && overallExit == 0) overallExit = exitCode
                // 关键命令（ip addr add / ip route add / iptables 挂 DNAT）就算失败也得继续跑完剩下的，
                // 因为 DHCP 还原要先清理规则
            }

            val result = CommandResult(
                success = overallExit == 0,
                exitCode = overallExit,
                stdout = globalStdout.toString().trim(),
                stderr = globalStderr.toString().trim()
            )
            Pair(result, diagnostics)
        }

    /**
     * 顺序执行多条命令（同一个 su 会话内）
     */
    suspend fun executeMultiple(commands: List<String>): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        var stdinWriter: DataOutputStream? = null
        var stdoutReader: BufferedReader? = null
        var stderrReader: BufferedReader? = null

        try {
            val sb = ProcessBuilder(SU_BINARY)
                .redirectErrorStream(false)
                .start()
            process = sb

            stdinWriter = DataOutputStream(sb.outputStream)
            stdoutReader = BufferedReader(InputStreamReader(sb.inputStream))
            stderrReader = BufferedReader(InputStreamReader(sb.errorStream))

            val stdoutBuf = StringBuilder()
            val stderrBuf = StringBuilder()

            commands.forEach { cmd ->
                stdinWriter.writeBytes(cmd + "\n")
                stdinWriter.flush()
            }

            // 写入一个带退出码的标记行，便于判断执行结束
            stdinWriter.writeBytes("echo \"${String.format(EXIT_CODE_MARKER, 0)}\"\n")
            stdinWriter.writeBytes(EXIT)
            stdinWriter.flush()

            var exitCode = -1
            var line: String?

            // 读取 stdout，直到读到退出码标记或流结束
            while (true) {
                line = stdoutReader.readLine() ?: break
                val marker = "EXIT_CODE:"
                if (line.startsWith(marker)) {
                    exitCode = try {
                        line.substring(marker.length).trim().toInt()
                    } catch (_: Exception) {
                        0
                    }
                    break
                }
                stdoutBuf.append(line).append('\n')
            }

            // 读取剩余 stderr
            while (stderrReader.readLine().also { line = it } != null) {
                stderrBuf.append(line).append('\n')
            }

            process.waitFor()
            if (exitCode == -1) exitCode = process.exitValue()

            CommandResult(
                success = exitCode == 0,
                exitCode = exitCode,
                stdout = stdoutBuf.toString().trim(),
                stderr = stderrBuf.toString().trim()
            )
        } catch (e: IOException) {
            CommandResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = (e.message ?: "IOException") + ": 无法执行 su，请确认设备已 Root 并授予本应用权限"
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                exitCode = -2,
                stdout = "",
                stderr = e.message ?: "Unknown exception while executing root commands"
            )
        } finally {
            safeClose(stdinWriter)
            safeClose(stdoutReader)
            safeClose(stderrReader)
            process?.destroy()
        }
    }

    private fun safeClose(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
            // ignore
        }
    }
}
