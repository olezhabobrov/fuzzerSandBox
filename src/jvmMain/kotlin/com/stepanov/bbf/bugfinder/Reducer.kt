package com.stepanov.bbf.bugfinder

import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.checkers.DiffBehaviorChecker
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.executor.checkers.DiffCompileChecker
import com.stepanov.bbf.bugfinder.executor.checkers.MultiCompilerCrashChecker
import com.stepanov.bbf.bugfinder.manager.Bug
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.reduktor.executor.CompilerTestChecker
import com.stepanov.bbf.reduktor.manager.TransformationManager
import org.apache.log4j.Logger
import java.io.File

object Reducer {

    fun reduce(bug: Bug): Project {
        if (bug.crashedProject.files.size > 1) {
            //return bug.crashedProject
            if (bug.type != BugType.BACKEND && bug.type != BugType.FRONTEND) return bug.crashedProject
            val checker = MultiCompilerCrashChecker(bug.crashedProject, bug.crashedProject.files.first(), bug.compilers.first(), bug.type)
            reduceProject(checker)//Project.createFromCode((reduced, bug.crashedProject.language)
            return bug.crashedProject
        }

        //if (bug.crashedProject.texts.size != 1 || bug.crashedProject.language == LANGUAGE.KJAVA) return bug.crashedProject
        //Saving to tmp
        val compilers = bug.compilers
        val proj = bug.crashedProject
        val checker = when (bug.type) {
            BugType.BACKEND, BugType.FRONTEND -> MultiCompilerCrashChecker(proj, proj.files.first(), compilers.first(), bug.type)
            BugType.DIFFCOMPILE -> DiffCompileChecker(proj, proj.files.first(), compilers)
            //BugType.DIFFBEHAVIOR -> DiffBehaviorChecker(proj, proj.files.first(), compilers)
            else -> return bug.crashedProject
        }
        reduceFile(checker)
//        if (shouldSave) saveFile(reduced)
        return proj
    }

//    fun reduce(path: String, compiler: CommonCompiler, shouldSave: Boolean = false): List<PsiFile> {
//        val f = File(path)
//        val files = if (f.isDirectory) f.listFiles().toList() else listOf(f)
//        val res = files.asSequence()
//            .map { PSICreator.getPSIForFile(it.absolutePath, false) }
//            .map {
//                reduceFile(
//                    it,
//                    MultiCompilerCrashChecker(compiler)
//                )
//            }
//            .map {
//                if (shouldSave) {
//                    saveFile(it)
//                    it
//                } else it
//            }
//        return res.toList()
//    }
//
//    fun reduceDiffBehavior(pathToFile: String, compilers: List<CommonCompiler>, shouldSave: Boolean = false): String {
//        val ktFile = PSICreator.getPSIForFile(pathToFile, false)
//        val res = reduceFile(
//            ktFile,
//            DiffBehaviorChecker(compilers)
//        )
//        if (shouldSave) saveFile(res)
//        return res.text
//    }
//
//    fun reduceDiffCompile(pathToFile: String, compilers: List<CommonCompiler>, shouldSave: Boolean = false): String {
//        val ktFile = PSICreator.getPSIForFile(pathToFile, false)
//        val res = reduceFile(
//            ktFile,
//            DiffCompileChecker(compilers)
//        )
//        if (shouldSave) saveFile(res)
//        return res.text
//    }

    private fun saveFile(f: PsiFile) {
        val pathToSave = StringBuilder(f.name)
        pathToSave.insert(pathToSave.indexOfLast { it == '/' }, "/minimized")
        File(pathToSave.toString().substringBeforeLast('/')).mkdirs()
        File("$pathToSave").run {
            writeText(f.text)
        }
    }

    private fun reduceFile(checker: CompilerTestChecker) {
        try {
            TransformationManager(checker).doTransformationsForFile()
        } catch (e: IllegalArgumentException) {
            return
        }
    }

    private fun reduceProject(checker: CompilerTestChecker) {
        var oldText = checker.project.files.map { it.text }.joinToString("\n").trim().filter { !it.isWhitespace() }
        while (true) {
            TransformationManager(checker).doProjectTransformations()
            val newText = checker.project.files.map { it.text }.joinToString("\n").trim().filter { !it.isWhitespace() }
            if (oldText == newText) break
            else oldText = newText
        }
    }
//    {
//        val files =
//            path.split(" ").map {
//                if (it.endsWith(".java"))
//                    PSICreator.getPsiForJava(File(it).readText(), Factory.file.project) to it
//                else PSICreator.getPSIForFile(it) to it
//            }
//        log.debug("START TO REDUCE PROJECT")
//        var oldText = files.map { it.first.text }.joinToString("\n").trim().filter { !it.isWhitespace() }
//        var reducedFiles: List<Pair<PsiFile, String>> = files
//        while (true) {
//            val res = TransformationManager(reducedFiles).doProjectTransformations(reducedFiles, checker)
//            val newText = res.map { it.text }.joinToString("\n").trim().filter { !it.isWhitespace() }
//            reducedFiles = res.zip(files.map { it.second })
//            if (oldText == newText) break
//            else oldText = newText
//            log.debug("ONE MORE ITERATION $oldText\n\n$newText\n\n")
//        }
//        return reducedFiles.map { it.first }
//    }

//    private fun reduceFile(file: KtFile, checker: CompilerTestChecker): KtFile = TODO()
//    {
//        return TransformationManager(listOf(file to file.name))
//            .doTransformationsForFile(file, checker)
//    }

    private val log = Logger.getLogger("transformationManagerLog")

}