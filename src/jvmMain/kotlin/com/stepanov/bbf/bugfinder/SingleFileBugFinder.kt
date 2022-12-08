package com.stepanov.bbf.bugfinder

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.checkers.CoverageGuider
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.checkers.PerformanceOracle
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.compilers.KJCompiler
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import coverage.MyMethodBasedCoverage
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.system.exitProcess

class SingleFileBugFinder(pathToFile: String) : BugFinder(pathToFile) {

    fun findBugsInFile() {
        println("Let's go")
        ++counter
        log.debug("Name = $dir")
        var project = Project.createFromCode(File(dir).readText())
        PSICreator.curProject = project
        if (project.files.isEmpty()) {
            log.debug("Cant create project")
            return
        }
        //TODO fuzz with coroutines also
        if (project.configuration.isWithCoroutines()) return
        if (project.files.size > 1 && project.language == LANGUAGE.KOTLIN) {
            project = project.convertToSingleFileProject()
        }
        //Add some imports
        for (f in project.files.map { it.psiFile }) {
            if (f !is KtFile) continue
            f.addImport("kotlin.properties", true)
            f.addImport("kotlin.reflect", true)
            f.addImport("kotlin.math", true)
        }
        val compilers = CompilerArgs.getCompilersList().mapNotNull { compiler ->
            if (project.language == LANGUAGE.KJAVA) {
                if (compiler.compilerInfo.startsWith("JVM")) {
                    KJCompiler(compiler.arguments)
                } else null
            } else compiler
        }
        val compilersConf = BBFProperties.getStringGroupWithoutQuotes("BACKENDS")
        val filterBackends = compilersConf.map { it.key }
        if (filterBackends.any { project.isBackendIgnores(it) }) {
            //TODO disable?
            log.debug("Ignore some of backends")
        }
        if (compilers.any { !it.checkCompiling(project) }) {
            log.debug("Can not compile $dir")
            return
        }
        log.debug("Start to mutate")
        log.debug("BEFORE = $project")
//            CompilerArgs.isInstrumentationMode = false
        //ProjectPreprocessor.preprocess(project, null)
        if (compilers.any { !it.checkCompiling(project) }) {
            log.debug("=(")
            exitProcess(0)
        }
        if (CompilerArgs.isGuidedByCoverage) {
            CoverageGuider.init("", project)
            CoverageGuider.getCoverage(project, compilers)
            MyMethodBasedCoverage.methodProbes.clear()
        }
        if (CompilerArgs.isPerformanceMode) {
            PerformanceOracle.init(project, CompilerArgs.getCompilersList())
        }
        //noLastLambdaInFinallyBlock temporary for avoiding duplicates bugs
        mutate(project, project.files.first(), compilers, listOf(::noBoxFunModifying, ::noLastLambdaInFinallyBlock))
        exitProcess(0)
//            //Save mutated file
//            if (CompilerArgs.shouldSaveMutatedFiles) {
//                val pathToNewTests = CompilerArgs.dirForNewTests
//                File(pathToNewTests).mkdirs()
//                val pathToSave = "$pathToNewTests/${Random().getRandomVariableName(10)}.kt"
//                File(pathToSave).writeText(resultingMutant.text)
//            }
        log.debug("Mutated = $project")
        return
    }

    var counter = 0
}