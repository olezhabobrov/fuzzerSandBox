package com.stepanov.bbf.bugfinder

import com.stepanov.bbf.bugfinder.executor.*
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.compilers.KJCompiler
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.preprocessor.KtProjectPreprocessor
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import java.io.File
import kotlin.random.Random

class ProjectBugFinder(dir: String) : BugFinder(dir) {

//    fun findBugsInKJProjects() {
//        compilers.replaceAll { KJCompiler((it as JVMCompiler).arguments) }
//        val file = File(dir).listFiles().random()
//        val files =
//            file.readText()
//                .split(Regex("(//File)|(// FILE)"))
//                .filter { it.contains(".java") || it.contains(".kt") }
//                .map { "// FILE$it" }
//                .map {
//                    if (it.getFileLanguageIfExist() == LANGUAGE.KOTLIN) PSICreator.getPSIForText(it)
//                    else PSICreator.getPsiForJava(it, Factory.file.project)
//                }
//        val checker = CompilationChecker(compilers)
//        val res = checker.isCompilationSuccessful(Project(files.map { it.text }, null, LANGUAGE.KJAVA))
//        log.debug("Try to compile $res")
//        if (!res) return
//        //Execute mutations?
//        val mutants = files.map { it.text }.toMutableList()
//        log.debug("Project:\n$mutants")
//        for ((i, file) in files.withIndex()) {
//            //if (file.text.getFileLanguageIfExist() == LANGUAGE.JAVA) continue
//            log.debug("File $i from ${files.size - 1} mutations began")
//            val creator = PSICreator
//            val psi =
//                if (file.text.getFileLanguageIfExist() == LANGUAGE.JAVA) creator.getPsiForJava(
//                    file.text,
//                    file.project
//                )
//                else creator.getPSIForText(file.text)
//            val m = makeMutant(
//                psi,
//                creator.ctx,
//                Project(mutants.getAllWithout(i), null, LANGUAGE.KJAVA),
//                listOf(::noBoxFunModifying)
//            )
//            mutants[i] = m.text
//        }
//        val proj = Project(mutants, null, LANGUAGE.KJAVA)
//        log.debug("Res after mutation = ${proj.texts}")
//        TracesChecker(compilers).compareTraces(proj)
//    }

//    fun findBugsInProjects() {
//        val dir = File(dir).listFiles()!!
//        val numOfFiles = 2/*Random.nextInt(2, 4)*/
//        val textFiles = (1..numOfFiles)
//            .map { dir[Random.nextInt(0, dir.size)] }
//            .map { println(it.name); it.readText() }
////        val textFiles = listOf(File("tmp/test1.kt").readText(), File("tmp/test2.kt").readText())
//        val preprocessedProject = KtProjectPreprocessor.preprocess(textFiles, Checker(compilers)) ?: return
//        //Execute mutations?
//        val mutants = preprocessedProject.map { it.text }.toMutableList()
//        log.debug("Project:\n$mutants")
//        for ((i, file) in preprocessedProject.withIndex()) {
//            log.debug("File $i from ${preprocessedProject.size - 1} mutations began")
//            val creator = PSICreator
//            val m = makeMutant(
//                creator.getPSIForText(file.text),
//                creator.ctx!!,
//                Project(mutants.getAllWithout(i)),
//                listOf()//listOf(::noBoxFunModifying)
//            )
//            mutants[i] = m.text
//        }
//        val proj = Project(mutants)
//        log.debug("Res after mutation = ${proj.getCommonTextWithDefaultPath()}")
//        TracesChecker(compilers).compareTraces(proj)
//        return
//    }
}