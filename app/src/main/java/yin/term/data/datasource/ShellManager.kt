package com.yin.term.data.datasource

import android.os.Build
import android.os.SystemClock
import com.yin.term.core.AppDispatchers
import com.yin.term.domain.model.TerminalLog
import com.yin.term.domain.model.TerminalState
import com.yin.term.domain.repository.SettingsRepository
import com.yin.term.domain.repository.TerminalLogRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.Volatile

class ShellManager(
    private val settingsRepository: SettingsRepository,
    private val terminalLogRepository: TerminalLogRepository,
    private val dispatchers: AppDispatchers
) {
    companion object {
        @Volatile
        var instance: ShellManager? = null
            private set

        private val WHITESPACE_REGEX = Regex("\\s+")

        private val morseMap = mapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
            'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
            'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
            'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
            'Y' to "-.--", 'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
            '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
            ' ' to "/"
        )

        private val reverseMorseMap: Map<String, Char> by lazy {
            morseMap.entries.associate { (k, v) -> v to k }
        }
    }

    init {
        instance = this
    }

    val terminalSession = TerminalSession(terminalLogRepository, dispatchers)
    val currentDirectory: StateFlow<String> get() = terminalSession.currentDirectory
    val isRootActive: StateFlow<Boolean> get() = terminalSession.isRootActive

    suspend fun initialize(preferRoot: Boolean = true) {
        terminalSession.ensureSessionStarted(preferRoot)
    }

    suspend fun execute(command: String, isRoot: Boolean = true) {
        withContext(dispatchers.io) {
            try {
                terminalSession.ensureSessionStarted(preferRoot = isRoot)
                val shellCommands = mutableListOf<String>()

                for (rawLine in command.lines()) {
                    val trimmed = rawLine.trim()
                    if (trimmed.isEmpty()) continue
                    if (!executedAsCustomCommand(settingsRepository, terminalLogRepository, trimmed)) {
                        shellCommands.add(rawLine)
                    }
                }

                if (shellCommands.isNotEmpty()) {
                    terminalSession.executeCommand(
                        command = shellCommands.joinToString("\n"),
                        preferRoot = isRoot
                    )
                }
            } catch (e: Exception) {
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Error, "${e.message ?: "Execution error"}\n")
                )
            }
        }
    }

    suspend fun cancel() {
        terminalSession.sendInterrupt()
    }

    private suspend fun executedAsCustomCommand(
        settingsRepository: SettingsRepository,
        terminalLogRepository: TerminalLogRepository,
        command: String
    ): Boolean {
        val tokens = command.split(WHITESPACE_REGEX)
        if (tokens.isEmpty() || tokens[0].isEmpty()) return false

        when (val baseCommand = tokens[0].lowercase(Locale.ROOT)) {
            "help" -> {
                val helpText = """
                ===== SYSTEM =====
                • help - Show command list
                • about - App & engine info
                • clear / cls - Clear terminal logs
                • sysinfo - Hardware & OS details
                • uptime - System uptime
                • datetime - Current date & time
                • reset - Restart persistent shell session

                ===== CUSTOMIZE =====
                • font [size] / def - Terminal font size (5-25)
                • color1 [r g b] / def - Normal text RGB
                • color2 [r g b] / def - Error text RGB
                • color3 [r g b] / def - Info text RGB

                ===== TEXT & DEV =====
                • random [a] [b] | uuid | length [txt] | case [up/low] [txt]
                • wordcount [txt] | sort [w...] | shuffle [w...]
                • reverse [txt] | palindrome [txt] | regex [pat] [txt]
                • base64 [enc/dec] [txt] | hash [md5/sha1/sha256] [txt]
                • url [enc/dec] [txt] | rot13 [txt] | morse [enc/dec] [txt]
                • binary [enc/dec] [txt] | hex [enc/dec] [txt] | ascii [char]
                • pass [len] | json [validate/format] [txt]
                """.trimIndent()
                terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$helpText\n"))
            }

            "about" -> {
                val aboutText = """
                __   ___     _____
                \ \ / (_)_ _|_   _|__ _ _ _ __
                 \ V /| | ' \ | |/ -_) '_| '  \
                  |_| |_|_||_||_|\___|_| |_|_|_|
                YinTerm v1.0 — Persistent Root Shell & Python 3.13
                """.trimIndent()
                terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$aboutText\n"))
            }

            "reset" -> {
                terminalSession.restartSession(preferRoot = true)
                terminalLogRepository.addLog(
                    TerminalLog(TerminalState.Success, "Shell session restarted.\n")
                )
            }

            "color", "color1", "color2", "color3" -> {
                val type = if (baseCommand == "color") 1 else baseCommand.last().digitToInt()
                handleColorCommand(tokens, type, settingsRepository, terminalLogRepository)
            }

            "font" -> {
                if (tokens.size == 2) {
                    if (tokens[1] == "def") {
                        settingsRepository.setLogFontSize(12)
                        terminalLogRepository.addLog(
                            TerminalLog(TerminalState.Success, "Font size reset to 12.\n")
                        )
                    } else {
                        val fontSize = tokens[1].toIntOrNull()
                        if (fontSize != null && fontSize in 5..25) {
                            settingsRepository.setLogFontSize(fontSize)
                            terminalLogRepository.addLog(
                                TerminalLog(TerminalState.Success, "Font size set to $fontSize.\n")
                            )
                        } else {
                            terminalLogRepository.addLog(
                                TerminalLog(TerminalState.Error, "Invalid size (5-25).\n")
                            )
                        }
                    }
                } else {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Error, "Usage: font [size] or [def]\n")
                    )
                }
            }

            "random" -> {
                if (tokens.size == 3) {
                    val num1 = tokens[1].toIntOrNull()
                    val num2 = tokens[2].toIntOrNull()
                    if (num1 != null && num2 != null) {
                        val min = minOf(num1, num2)
                        val max = maxOf(num1, num2)
                        terminalLogRepository.addLog(
                            TerminalLog(TerminalState.Success, "${(min..max).random()}\n")
                        )
                    } else {
                        terminalLogRepository.addLog(
                            TerminalLog(TerminalState.Error, "Enter valid numbers.\n")
                        )
                    }
                } else {
                    terminalLogRepository.addLog(
                        TerminalLog(TerminalState.Error, "Usage: random [a] [b]\n")
                    )
                }
            }

            "clear", "cls" -> terminalLogRepository.clearLogs()

            "sysinfo" -> {
                val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
                val cores = Runtime.getRuntime().availableProcessors()
                val maxVmMem = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                val info = """
                [ HARDWARE ] ${Build.BRAND} ${Build.MODEL} (${Build.DEVICE})
                [ CPU/MEM  ] $cores cores | ABI: $primaryAbi | JVM: ${maxVmMem}MB
                [ OS/BUILD ] Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | Patch: ${Build.VERSION.SECURITY_PATCH}
                [ KERNEL   ] ${System.getProperty("os.version")} | Uptime: ${getSystemUptime()}
                """.trimIndent()
                terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$info\n"))
            }

            "uptime" -> terminalLogRepository.addLog(
                TerminalLog(TerminalState.Success, "Uptime: ${getSystemUptime()}\n")
            )

            "datetime" -> {
                val current = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$current\n"))
            }

            "length" -> {
                if (tokens.size >= 2) {
                    val text = tokens.drop(1).joinToString(" ")
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "Length: ${text.length}\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: length [text]\n"))
                }
            }

            "case" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    val result = if (action == "up" || action == "upper") {
                        text.uppercase(Locale.getDefault())
                    } else {
                        text.lowercase(Locale.getDefault())
                    }
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: case [up/low] [text]\n"))
                }
            }

            "url" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    runCatching {
                        if (action == "enc" || action == "encode") URLEncoder.encode(text, "UTF-8")
                        else URLDecoder.decode(text, "UTF-8")
                    }.onSuccess {
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$it\n"))
                    }.onFailure {
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "URL operation failed.\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: url [enc/dec] [text]\n"))
                }
            }

            "base64" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    when (action) {
                        "enc", "encode" -> {
                            val encoded = Base64.getEncoder().encodeToString(text.toByteArray())
                            terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$encoded\n"))
                        }
                        "dec", "decode" -> {
                            runCatching { String(Base64.getDecoder().decode(text)) }
                                .onSuccess { terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$it\n")) }
                                .onFailure { terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Invalid Base64.\n")) }
                        }
                        else -> terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: base64 [enc/dec] [text]\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: base64 [enc/dec] [text]\n"))
                }
            }

            "hash" -> {
                if (tokens.size >= 3) {
                    val targetAlgo = when (tokens[1].lowercase(Locale.ROOT)) {
                        "md5" -> "MD5"
                        "sha1" -> "SHA-1"
                        "sha256" -> "SHA-256"
                        else -> null
                    }
                    val text = tokens.drop(2).joinToString(" ")
                    if (targetAlgo != null) {
                        val hashString = MessageDigest.getInstance(targetAlgo)
                            .digest(text.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$hashString\n"))
                    } else {
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Use: md5, sha1, sha256\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: hash [algo] [text]\n"))
                }
            }

            "uuid" -> terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "${UUID.randomUUID()}\n"))

            "rot13" -> {
                if (tokens.size >= 2) {
                    val text = tokens.drop(1).joinToString(" ")
                    val result = text.map { c ->
                        when (c) {
                            in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                            in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                            else -> c
                        }
                    }.joinToString("")
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: rot13 [text]\n"))
                }
            }

            "reverse" -> {
                if (tokens.size >= 2) {
                    val text = tokens.drop(1).joinToString(" ")
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "${text.reversed()}\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: reverse [text]\n"))
                }
            }

            "palindrome" -> {
                if (tokens.size >= 2) {
                    val normalized = tokens.drop(1).joinToString("").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
                    val msg = if (normalized == normalized.reversed()) "Yes, palindrome.\n" else "Not a palindrome.\n"
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, msg))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: palindrome [text]\n"))
                }
            }

            "morse" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    when (action) {
                        "enc", "encode" -> {
                            val result = text.uppercase(Locale.ROOT).map { morseMap[it] ?: it.toString() }.joinToString(" ")
                            terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                        }
                        "dec", "decode" -> {
                            val result = text.trim().split(' ').joinToString("") { reverseMorseMap[it]?.toString() ?: it }
                            terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                        }
                        else -> terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: morse [enc/dec] [text]\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: morse [enc/dec] [text]\n"))
                }
            }

            "binary" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    when (action) {
                        "enc", "encode" -> {
                            val result = text.toByteArray().joinToString(" ") { Integer.toBinaryString(it.toInt() and 0xFF).padStart(8, '0') }
                            terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                        }
                        "dec", "decode" -> {
                            runCatching {
                                String(text.trim().split(' ').map { it.toInt(2).toByte() }.toByteArray())
                            }.onSuccess {
                                terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$it\n"))
                            }.onFailure {
                                terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Invalid binary.\n"))
                            }
                        }
                        else -> terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: binary [enc/dec] [text]\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: binary [enc/dec] [text]\n"))
                }
            }

            "hex" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    when (action) {
                        "enc", "encode" -> {
                            val result = text.toByteArray().joinToString("") { "%02x".format(it) }
                            terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                        }
                        "dec", "decode" -> {
                            runCatching {
                                String(text.trim().replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                            }.onSuccess {
                                terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$it\n"))
                            }.onFailure {
                                terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Invalid hex.\n"))
                            }
                        }
                        else -> terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: hex [enc/dec] [text]\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: hex [enc/dec] [text]\n"))
                }
            }

            "ascii" -> {
                if (tokens.size == 2 && tokens[1].length == 1) {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "${tokens[1][0].code}\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: ascii [single char]\n"))
                }
            }

            "wordcount" -> {
                if (tokens.size >= 2) {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "Words: ${tokens.drop(1).size}\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: wordcount [text]\n"))
                }
            }

            "sort" -> {
                if (tokens.size >= 2) {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "${tokens.drop(1).sorted().joinToString(" ")}\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: sort [w1] [w2] ...\n"))
                }
            }

            "shuffle" -> {
                if (tokens.size >= 2) {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "${tokens.drop(1).shuffled().joinToString(" ")}\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: shuffle [w1] [w2] ...\n"))
                }
            }

            "pass" -> {
                val length = tokens.getOrNull(1)?.toIntOrNull()
                if (tokens.size == 2 && length != null && length in 4..128) {
                    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
                    val result = (1..length).map { chars.random() }.joinToString("")
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: pass [4-128]\n"))
                }
            }

            "json" -> {
                if (tokens.size >= 3) {
                    val action = tokens[1].lowercase(Locale.ROOT)
                    val text = tokens.drop(2).joinToString(" ")
                    runCatching {
                        val parsed: Any = runCatching { JSONObject(text) }.getOrElse { JSONArray(text) }
                        when (action) {
                            "validate" -> "Valid JSON."
                            "format" -> when (parsed) {
                                is JSONObject -> parsed.toString(2)
                                is JSONArray -> parsed.toString(2)
                                else -> text
                            }
                            else -> throw IllegalArgumentException()
                        }
                    }.onSuccess {
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$it\n"))
                    }.onFailure {
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Invalid JSON or syntax.\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: json [validate/format] [text]\n"))
                }
            }

            "regex" -> {
                if (tokens.size >= 3) {
                    val pattern = tokens[1]
                    val text = tokens.drop(2).joinToString(" ")
                    runCatching {
                        Regex(pattern).findAll(text).map { it.value }.toList()
                    }.onSuccess { matches ->
                        val result = if (matches.isEmpty()) "No match." else "Matches: ${matches.joinToString(", ")}"
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "$result\n"))
                    }.onFailure {
                        terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Invalid regex pattern.\n"))
                    }
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Usage: regex [pattern] [text]\n"))
                }
            }

            else -> return false
        }
        return true
    }

    private fun getSystemUptime(): String {
        val upTime = SystemClock.elapsedRealtime()
        val hours = upTime / 3600000
        val minutes = (upTime / 60000) % 60
        val seconds = (upTime / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private suspend fun handleColorCommand(
        tokens: List<String>,
        colorType: Int,
        settingsRepository: SettingsRepository,
        terminalLogRepository: TerminalLogRepository
    ) {
        val usage = "Usage: color$colorType [r g b] or [def]\n"
        when (tokens.size) {
            2 -> {
                if (tokens[1] == "def") {
                    updateRepositoryColor(settingsRepository, colorType, -1, -1, -1)
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "Color $colorType reset.\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, usage))
                }
            }
            4 -> {
                val r = tokens[1].toIntOrNull()
                val g = tokens[2].toIntOrNull()
                val b = tokens[3].toIntOrNull()
                if (r != null && g != null && b != null && r in 0..255 && g in 0..255 && b in 0..255) {
                    updateRepositoryColor(settingsRepository, colorType, r, g, b)
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Success, "Color $colorType applied.\n"))
                } else {
                    terminalLogRepository.addLog(TerminalLog(TerminalState.Error, "Values must be 0-255.\n"))
                }
            }
            else -> terminalLogRepository.addLog(TerminalLog(TerminalState.Error, usage))
        }
    }

    private suspend fun updateRepositoryColor(
        repo: SettingsRepository,
        type: Int,
        r: Int,
        g: Int,
        b: Int
    ) {
        when (type) {
            1 -> repo.setLogSuccessFontColor(r, g, b)
            2 -> repo.setLogErrorFontColor(r, g, b)
            3 -> repo.setLogInfoFontColor(r, g, b)
        }
    }
}