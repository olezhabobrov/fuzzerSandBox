package com.stepanov.bbf.bugfinder.manager

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.util.getRandomVariableName
import java.io.File
import java.util.*


//TODO Maybe add crashing message in comments
object FileReporter : Reporter {

    fun saveRegularBug(bug: Bug) {
        val compilerBugDir = bug.compilerVersion.filter { it != ' ' }
        val resDir = CompilerArgs.resultsDir
        val randomName = Random().getRandomVariableName(5)
        val newPath =
            if (resDir.endsWith('/')) "$resDir$compilerBugDir/${bug.type.name}_$randomName.kt"
            else "$resDir/$compilerBugDir/${bug.type.name}_$randomName.kt"
        File(newPath).writeText(bug.crashedProject.moveAllCodeInOneFile())
    }

    fun saveDiffBug(bug: Bug, type: String) {
        val resDir = CompilerArgs.resultsDir
        val newPath =
            if (resDir.endsWith('/')) "${resDir}diff$type/${Random().getRandomVariableName(7)}.kt"
            else "${resDir}/diff$type/${Random().getRandomVariableName(7)}.kt"
        val diffCompilers = "// Different ${type.toLowerCase()} happens on:${bug.compilerVersion}"
        File(newPath.substringBeforeLast('/')).mkdirs()
        File(newPath).writeText("$diffCompilers\n${bug.crashedProject.moveAllCodeInOneFile()}")
    }

    override fun dump(bugs: List<Bug>) {
        val isFrontendOrBackendBug = bugs.size == 2 && (bugs.first().type == BugType.FRONTEND || bugs.first().type == BugType.BACKEND)
        val withoutDuplicates =
            if (isFrontendOrBackendBug) bugs.drop(1) else bugs
        for (bug in withoutDuplicates) {
            val resDir = CompilerArgs.resultsDir
            val name = Random().getRandomVariableName(7) +
                    if (bug.crashedProject.files.size == 1) "_FILE" else "_PROJECT"
            val newPath = when (bug.type) {
                BugType.BACKEND, BugType.FRONTEND -> "$resDir${bug.compilerVersion.filter { it != ' ' }}/${bug.type.name}_$name.kt"
                BugType.DIFFCOMPILE -> "$resDir/diffCompile/$name.kt"
                BugType.DIFFBEHAVIOR -> "$resDir/diffBehavior/$name.kt"
                BugType.DIFFABI -> "$resDir/diffABI/$name.kt"
                BugType.PERFORMANCE -> "$resDir/performance/$name.kt"
                else -> return
            }
            File(newPath.substringBeforeLast('/')).mkdirs()
            val info = "// Bug happens on ${bug.compilerVersion} ver ${CompilerArgs.compilerVersion}"
            val commentedStackTrace =
                if (bug.type == BugType.BACKEND || bug.type == BugType.FRONTEND) {
                    "// STACKTRACE:\n${bug.msg.split("\n").joinToString("\n") { "// $it" }}"
                } else {
                    ""
                }
            if (bug.type == BugType.DIFFABI) {
                File(newPath.replaceAfter('.', "html")).writeText(bug.msg)
            }
            if (isFrontendOrBackendBug) {
                val pathForOriginal =
                    "$resDir${bug.compilerVersion.filter { it != ' ' }}/${bug.type.name}_${name}_ORIGINAL.kt"
                File(pathForOriginal).writeText("$info\n${bugs.first().crashedProject.moveAllCodeInOneFile()}\n$commentedStackTrace")
            }
            File(newPath).writeText("$info\n${bug.crashedProject.moveAllCodeInOneFile()}\n$commentedStackTrace")
        }
    }

}