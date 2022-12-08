package com.stepanov.bbf.bugfinder.executor

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.Stream
import com.stepanov.bbf.reduktor.executor.KotlincInvokeStatus
import org.apache.commons.exec.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import java.io.ByteArrayOutputStream

data class CompilationResult(val status: Int, val pathToCompiled: String)

enum class COMPILE_STATUS {
    OK, ERROR, BUG
}

abstract class CommonCompiler {

    abstract val arguments: String
    abstract fun checkCompiling(project: Project): Boolean
    abstract fun getErrorMessageWithLocation(project: Project): Pair<String, List<CompilerMessageSourceLocation>>
    abstract fun tryToCompile(project: Project): KotlincInvokeStatus
    abstract fun isCompilerBug(project: Project): Boolean
    abstract fun compile(project: Project, includeRuntime: Boolean = true): CompilationResult
    abstract fun compile(project: Project, numberOfExecutions: Int = 1, includeRuntime: Boolean = true): CompilationResult
    abstract fun exec(path: String, streamType: Stream = Stream.INPUT, mainClass: String = ""): String

    abstract val compilerInfo: String
    abstract var pathToCompiled: String

    fun getErrorMessage(project: Project): String = getErrorMessageWithLocation(project).first
    fun getErrorMessageForText(text: String): String = getErrorMessageForTextWithLocation(text).first
    fun getErrorMessageForTextWithLocation(text: String): Pair<String, List<CompilerMessageSourceLocation>> =
        getErrorMessageWithLocation(Project.createFromCode(text))

    fun tryToCompileWithStatus(project: Project): COMPILE_STATUS {
        val status = tryToCompile(project)
        return when {
            status.hasException -> COMPILE_STATUS.BUG
            status.hasCompilationError() || status.hasTimeout -> COMPILE_STATUS.ERROR
            status.isCompileSuccess -> COMPILE_STATUS.OK
            else -> COMPILE_STATUS.ERROR
        }
    }

    fun tryToCompileWithStatusAndExecutionTime(project: Project): Pair<COMPILE_STATUS, Long> {
        val kotlincInvokeStatus = tryToCompile(project)
        val compilationStatus =
            when {
                kotlincInvokeStatus.hasException -> COMPILE_STATUS.BUG
                kotlincInvokeStatus.hasCompilationError() || kotlincInvokeStatus.hasTimeout -> COMPILE_STATUS.ERROR
                kotlincInvokeStatus.isCompileSuccess -> COMPILE_STATUS.OK
                else -> COMPILE_STATUS.ERROR
            }
        return compilationStatus to kotlincInvokeStatus.compilerExecTimeInMlls
    }

    fun isCompilerBug(text: String): Boolean {
        if (text.trim().isEmpty()) return false
        return isCompilerBug(Project.createFromCode(text))
    }

    fun checkCompilingText(text: String): Boolean {
        if (text.trim().isEmpty()) return false
        return checkCompiling(Project.createFromCode(text))
    }

    fun commonExec(command: String, streamType: Stream = Stream.INPUT, timeoutSec: Long = 5L): String {
        val cmdLine = CommandLine.parse(command)
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        val executor = DefaultExecutor().also {
            it.watchdog = ExecuteWatchdog(timeoutSec * 1000)
            it.streamHandler = PumpStreamHandler(outputStream, errorStream)
        }
        try {
            executor.execute(cmdLine)
        } catch (e: ExecuteException) {
            executor.watchdog.destroyProcess()
            var streamOutput =  when (streamType) {
                Stream.INPUT -> outputStream.toString()
                Stream.ERROR -> errorStream.toString()
                else -> "" + errorStream.toString()
            }
            if (errorStream.toString().isEmpty() || errorStream.toString().contains("StackOverflow", true)) {
                streamOutput = "Exception timeout"
            }
            return streamOutput
            //return outputStream.toString()
        }
        return when (streamType) {
            Stream.INPUT -> outputStream.toString()
            Stream.ERROR -> errorStream.toString()
            Stream.BOTH -> "OUTPUTSTREAM:\n$outputStream ERRORSTREAM:\n$errorStream"
        }
    }

    fun getExecutionTime(path: String, streamType: Stream = Stream.BOTH, mainClass: String = ""): Pair<String, Long> {
        val startTime = System.currentTimeMillis()
        val execRes = exec(path, streamType, mainClass)
        return execRes to System.currentTimeMillis() - startTime
    }


    override fun toString(): String = compilerInfo
}

