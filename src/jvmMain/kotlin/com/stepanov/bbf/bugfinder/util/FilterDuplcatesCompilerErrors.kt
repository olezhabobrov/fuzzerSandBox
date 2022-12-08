package com.stepanov.bbf.bugfinder.util

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.manager.Bug
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.apache.log4j.Logger
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import org.jsoup.Jsoup
import java.io.File

//TODO need to be rewrited
object FilterDuplcatesCompilerErrors {

    private fun getBugType(errorMsg: String) =
        if (errorMsg.contains("Exception while analyzing expression")) BugType.FRONTEND else BugType.BACKEND

    fun simpleIsSameErrs(errorMsg: String, path2: String, compiler: CommonCompiler): Boolean {
        val originalBugType = getBugType(errorMsg)
        val proj2 = Project.createFromCode(File(path2).readText())
        val errorMsgForFile = compiler.getErrorMessage(proj2)
        val potentialDuplBugType = getBugType(errorMsgForFile)
        if (originalBugType != potentialDuplBugType) return false
        val k = newCheckErrsMatching(
            errorMsg,
            errorMsgForFile
        )
        if (k > 0.49) {
            log.debug("bug and $path2 are duplicates!!!")
            return true
        }
        val kStacktraces = newCheckErrsMatching(
            getStacktrace2(errorMsg),
            getStacktrace2(errorMsgForFile)
        )
        if (kStacktraces == 0.5) {
            log.debug("bug and $path2 are duplicates!!!")
            return true
        }
        log.debug("Comparing bug with $path2 $k stacks: $kStacktraces")
        return false
    }

    fun simpleHaveDuplicatesErrors(
        proj: Project,
        dir: String,
        compiler: CommonCompiler,
        excludedFileNames: List<String> = listOf()
    ): Boolean {
        val errorMsg = compiler.getErrorMessage(proj)
        return File(dir).listFiles()
            ?.filter { it.path.endsWith(".kt") || it.path.endsWith(".java") }
            ?.filter { it.name !in excludedFileNames }
            ?.filter { !it.name.contains("ORIGINAL") }
            ?.any {
            simpleIsSameErrs(
                errorMsg,
                it.absolutePath,
                compiler
            )
        } ?: false
    }

    fun haveSameDiffCompileErrors(
        proj: Project,
        dir: String,
        compilers: List<CommonCompiler>,
        strictMode: Boolean
    ): Boolean {
        val k1 = if (strictMode) 0.3 else 0.45
        val errorToLocation1 = compilers
            .map { it.compilerInfo to it.getErrorMessageWithLocation(proj) }
            .first { it.second.first.trim().isNotEmpty() }
            .let { "${it.first}\n${it.second.first}" to it.second.second.first().lineContent }
        proj.saveOrRemoveToTmp(false)

        for (file in File(dir).listFiles().filter { it.absolutePath.endsWith(".kt") }) {
            val proj2 = Project.createFromCode(file.readText())
            //Check if compiling or compiler bug
            if (compilers.map { it.isCompilerBug(proj2) }.any { it }) {
                file.delete()
                continue
            }
            val errorToLocation2 = compilers
                .map { it.compilerInfo to it.getErrorMessageWithLocation(proj2) }
                .firstOrNull { it.second.first.trim().isNotEmpty() }
                ?.let { "${it.first}\n${it.second.first}" to it.second.second.first().lineContent } ?: continue
            if (errorToLocation1.first.split("\n").first() != errorToLocation2.first.split("\n").first()) continue
            val diff = newCheckErrsMatching(errorToLocation1.first, errorToLocation2.first)
            log.debug("${errorToLocation1.first} \n${errorToLocation2.first}\n ${file.absolutePath} $diff\n\n")
            if (diff > 0.3) {
                val diff2 = newCheckErrsMatching(
                    errorToLocation1.second ?: "",
                    errorToLocation2.second ?: "0"
                )
                log.debug("proj ${file.absolutePath} $diff2")
                if (diff2 > k1) {
                    log.debug("proj and ${file.absolutePath} are duplicates")
                    return true
                }
            }
        }
        return false
    }

    fun produceCodeFromHTML(html: String): String =
        Jsoup.parse(html)
            .body().getElementsByTag("code")
            .map { it.ownText() }
            .filter { it != "---" && it.trim().isNotEmpty() }
            .sorted()
            .joinToString("\n")

    fun haveSameDiffABIErrors(
        bug: Bug
    ): Boolean {
        val dirWithSameBugs = bug.getDirWithSameTypeBugs()
        val html1 = produceCodeFromHTML(bug.msg)
        for (f in File(dirWithSameBugs).listFiles()!!.filter { it.name.endsWith("html") }) {
            val html2 = produceCodeFromHTML(f.readText())
            val k = newCheckErrsMatching(html1, html2)
            log.debug("Compare bug with ${f.name} $k")
            if (k > 0.45) {
                log.debug("DUPLICATE")
                return true
            }
        }
        return false
    }


    private fun getStacktrace(msg: String): String {
        val firstIndex = msg.indexOf("\nThe root cause was thrown at:")
        val lastIndex = msg.indexOf("Caused by:", firstIndex)
        if (firstIndex == -1 || lastIndex == -1) return ""
        val res = msg.substring(firstIndex, lastIndex)
        return res
    }

    fun getStacktrace2(msg: String): String =
        msg
            .split("Cause:")
            .last()
            .split("\n")
            .map { it.trim() }
            .filter { it.startsWith("at ") }
            .joinToString("\n")

    private fun checkErrsMatching(a: String, b: String): Double {
        val diffs = patch.diffMain(a, b)
        var sameNum = 0
        var difNum = 0
        for (dif in diffs) {
            when (dif.operation.name) {
                "EQUAL" -> sameNum += dif.text.length
                else -> difNum += dif.text.length
            }
        }
        return if (sameNum == 0) Double.MAX_VALUE else difNum.toDouble() / sameNum.toDouble()
    }

    fun newCheckErrsMatching(a: String, b: String): Double {
        if (a.length + b.length == 0) return Double.MAX_VALUE
        val diffs = patch.diffMain(a, b)
        var sameNum = 0
        var difNum = 0
        for (dif in diffs) {
            when (dif.operation.name) {
                "EQUAL" -> sameNum += dif.text.length
                else -> difNum += dif.text.length
            }
        }
        return if (sameNum == 0) Double.MIN_VALUE else sameNum.toDouble() / (a.length + b.length)
    }

    private val patch = DiffMatchPatch()
    private val log = Logger.getLogger("mutatorLogger")
}
