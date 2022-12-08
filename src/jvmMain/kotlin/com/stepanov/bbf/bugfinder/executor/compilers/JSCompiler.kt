package com.stepanov.bbf.bugfinder.executor.compilers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.CompilationResult
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.Stream
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.config.Services
import com.stepanov.bbf.reduktor.executor.KotlincInvokeStatus
import com.stepanov.bbf.reduktor.util.MsgCollector
import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class JSCompiler(override val arguments: String = "") : CommonCompiler() {

    override val compilerInfo: String
        get() = "JS $arguments"

    override var pathToCompiled: String = "tmp/tmp.js"

    override fun checkCompiling(project: Project): Boolean {
        val status = tryToCompile(project)
        return !MsgCollector.hasCompileError && !status.hasTimeout && !MsgCollector.hasException
    }

    override fun getErrorMessageWithLocation(project: Project): Pair<String, List<CompilerMessageSourceLocation>> {
        val status = tryToCompile(project)
        return status.combinedOutput to status.locations
    }

    override fun isCompilerBug(project: Project): Boolean =
        tryToCompile(project).hasException

    override fun compile(project: Project, includeRuntime: Boolean): CompilationResult {
        val projectWithMainFun = project.addMain()
        val path = projectWithMainFun.saveOrRemoveToTmp(true)
        val args = prepareArgs(projectWithMainFun, path, pathToCompiled)
        val status = executeCompiler(projectWithMainFun, args)
        if (status.hasException || status.hasTimeout || !status.isCompileSuccess) return CompilationResult(-1, "")
        val oldStr = FileReader(File(pathToCompiled)).readText()
        val newStr = "const kotlin = require(\"${CompilerArgs.pathToJsKotlinLib}kotlin.js\");\n" +
                "this['kotlin-test'] = require(\"${CompilerArgs.pathToJsKotlinLib}kotlin-test.js\");" +
                "\n\n$oldStr"
        val fw = FileWriter(pathToCompiled, false)
        val bw = BufferedWriter(fw)
        bw.write(newStr)
        bw.close()
        return CompilationResult(0, pathToCompiled)
    }

    override fun compile(project: Project, numberOfExecutions: Int, includeRuntime: Boolean): CompilationResult {
        TODO("Not yet implemented")
    }

    private fun prepareArgs(project: Project, path: String, destination: String): K2JSCompilerArguments {
        val destFile = File(destination)
        if (destFile.isFile) destFile.delete()
        else if (destFile.isDirectory) FileUtils.cleanDirectory(destFile)
        val projectArgs = project.getProjectSettingsAsCompilerArgs("JS") as K2JSCompilerArguments
        val compilerArgs =
            if (arguments.isEmpty())
                "$path -output $pathToCompiled".split(" ")
            else
                "$path $arguments -output $pathToCompiled".split(" ")
        projectArgs.apply { K2JSCompiler().parseArguments(compilerArgs.toTypedArray(), this) }
        projectArgs.libraries = CompilerArgs.jsStdLibPaths.joinToString(separator = ":")
        return projectArgs
    }

    private fun executeCompiler(project: Project, args: K2JSCompilerArguments): KotlincInvokeStatus {
        val compiler = K2JSCompiler()
        val services = Services.EMPTY
        MsgCollector.clear()
        val threadPool = Executors.newCachedThreadPool()
        val futureExitCode = threadPool.submit {
            compiler.exec(MsgCollector, services, args)
        }
        var hasTimeout = false
        var compilerWorkingTime: Long = -1
        try {
            val startTime = System.currentTimeMillis()
            futureExitCode.get(10L, TimeUnit.SECONDS)
            compilerWorkingTime = System.currentTimeMillis() - startTime
        } catch (ex: TimeoutException) {
            hasTimeout = true
            futureExitCode.cancel(true)
        } finally {
            project.saveOrRemoveToTmp(false)
        }
        val status = KotlincInvokeStatus(
            MsgCollector.crashMessages.joinToString("\n") +
                    MsgCollector.compileErrorMessages.joinToString("\n"),
            !MsgCollector.hasCompileError,
            MsgCollector.hasException,
            hasTimeout,
            compilerWorkingTime,
            MsgCollector.locations.toMutableList()
        )
        return status
    }

    override fun tryToCompile(project: Project): KotlincInvokeStatus {
        val path = project.saveOrRemoveToTmp(true)
        val trashDir = "${CompilerArgs.pathToTmpDir}/trash/"
        val args = prepareArgs(project, path, trashDir)
        return executeCompiler(project, args)
    }

    override fun exec(path: String, streamType: Stream, mainClass: String): String =
        commonExec("node $path", streamType)

    private fun analyzeErrorMessage(msg: String): Boolean = !msg.split("\n").any { it.contains(": error:") }

    private val log = Logger.getLogger("compilerErrorsLog")

}