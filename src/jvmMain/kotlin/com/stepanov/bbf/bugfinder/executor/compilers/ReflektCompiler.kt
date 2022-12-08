package com.stepanov.bbf.bugfinder.executor.compilers

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.Stream
import org.apache.commons.exec.*
import java.io.ByteArrayOutputStream


object ReflektCompiler {

    val timeoutSec = 30L

    fun getExecutionResult(pathToGradleProject: String, project: Project): String {
        project.saveOrRemoveToDirectory(true, "tmp/testProject/examples/src/main/kotlin/io/reflekt/example/")
        val command = "gradle -b tmp/testProject/examples/build.gradle.kts execReflekt"
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
            return errorStream.toString()
        } finally {
            project.saveOrRemoveToDirectory(false, "tmp/testProject/examples/src/main/kotlin/io/reflekt/example/")
        }
        return "OUTPUTSTREAM:\n$outputStream ERRORSTREAM:\n$errorStream"
    }
}