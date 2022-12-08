package com.stepanov.bbf.bugfinder.executor.checkers

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.COMPILE_STATUS
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.Bug
import com.stepanov.bbf.bugfinder.manager.BugManager
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.tracer.Tracer
import com.stepanov.bbf.bugfinder.util.StatisticCollector
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import coverage.MyMethodBasedCoverage
import org.apache.log4j.Logger
import kotlin.math.absoluteValue

//Project adaptation
open class CompilationChecker(private val compilers: List<CommonCompiler>) {

    constructor(compiler: CommonCompiler) : this(listOf(compiler))

    //Back compatibility
    fun checkTextCompiling(text: String): Boolean = checkCompilingWithBugSaving(Project.createFromCode(text), null)
    fun checkCompilingWithBugSaving(file: PsiFile): Boolean = checkTextCompiling(file.text)


    private fun createPsiAndCheckOnErrors(text: String, language: LANGUAGE): Boolean =
        when (language) {
            LANGUAGE.JAVA -> PSICreator.getPsiForJava(text, Factory.file.project)
            else -> Factory.psiFactory.createFile(text)
        }.let { tree ->
            tree.getAllPSIChildrenOfType<PsiErrorElement>().isEmpty() && additionalConditions.all { it.invoke(tree) }
        }

    //FALSE IF ERROR
    private fun checkSyntaxCorrectnessAndAddCond(project: Project, curFile: BBFFile?) =
        curFile?.let {
            createPsiAndCheckOnErrors(curFile.text, curFile.getLanguage())
        } ?: project.files.any { createPsiAndCheckOnErrors(it.text, it.getLanguage()) }


    fun checkCompiling(project: Project): Boolean {
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        checkedConfigurations[allTexts]?.let { log.debug("Already checked"); return it }
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, null)) {
            log.debug("Wrong syntax or breaks conditions")
            checkedConfigurations[allTexts] = false
            return false
        }
        val statuses = compileAndGetStatuses(project)
        return if (statuses.all { it == COMPILE_STATUS.OK }) {
            checkedConfigurations[allTexts] = true
            true
        } else {
            checkedConfigurations[allTexts] = false
            false
        }
    }

//    private fun compareCompilerWorkingTimes(compilerWorkingTimes: List<Long>, isExecution: Boolean): Boolean {
//        if (isExecution) println("EXECUTION!!")
//        for (i in compilers.indices) {
//            for (j in i + 1 until compilers.size) {
//                val fExTime = compilerWorkingTimes[i]
//                val sExTime = compilerWorkingTimes[j]
//                val slowerBackend = if (fExTime > sExTime) i else j
//                val fasterBackend = i + j - slowerBackend
//                val compilationTimeDifference = abs(fExTime - sExTime)
//                val compilationTimeDifferenceInPercents =
//                    if (fExTime == sExTime || fExTime == 0L || sExTime == 0L) 0.0
//                    else maxOf(fExTime, sExTime).toDouble() / minOf(fExTime, sExTime) * 100.0
//                println("BACKEND ${compilers[slowerBackend]} slower then ${compilers[fasterBackend]} on $compilationTimeDifference ms in percents $compilationTimeDifferenceInPercents")
//                return true // TODO!!
//            }
//        }
//        return true
//    }

    fun checkCompilingWithBugSaving(project: Project, curFile: BBFFile? = null): Boolean {
        log.debug("Compilation checking started")
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        checkedConfigurations[allTexts]?.let { log.debug("Already checked"); return it }
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, curFile)) {
            log.debug("Wrong syntax or breaks conditions")
            StatisticCollector.incField("Incorrect programs")
            checkedConfigurations[allTexts] = false
            return false
        }
        val (statuses, compilationTimes) =
            compileAndGetStatusesWithExecutionTime(project).let { it.map { it.first } to it.map { it.second } }
        when {
            statuses.all { it == COMPILE_STATUS.OK } -> {
                if (CompilerArgs.isPerformanceMode) {
                    //-1 not interesting, 0 - ok, 1 - interesting
                    var compilationRetValue = -1
                    var executionRetValue = -1
                    val compilationTimesDifferenceInPerc =
                        kotlin.math.abs(compilationTimes.first()).toDouble() / compilationTimes.last().absoluteValue
                    val compInterval = PerformanceOracle.compilationConfInterval.let { it.first..it.second }
                    println("COMPILATION TIMES = $compilationTimes")
                    println("COMPILATION TIME DIFFERENCE = $compilationTimesDifferenceInPerc")
                    println("INTERVAL =  $compInterval")
                    if (compilationTimesDifferenceInPerc !in compInterval) {
                        if (compilationTimes.first() > compilationTimes.last() && compilationTimesDifferenceInPerc > compInterval.endInclusive) {
                            val compSigma = PerformanceOracle.compilationSigma
                            val compConfInterval = PerformanceOracle.compilationConfInterval
                            val compTimeIntervalMedian = (compConfInterval.second + compConfInterval.first) / 2.0
                            val newMed = compTimeIntervalMedian * 0.62  + compilationTimesDifferenceInPerc * 0.38
                            val newInterval = newMed - compSigma to newMed + compSigma
                            PerformanceOracle.compilationConfInterval = newInterval
                            compilationRetValue = 1
                        } else {
                            compilationRetValue = -1
                        }
                    } else {
                        compilationRetValue = 0
                    }
                    val executionTimes = mutableListOf<Long>()
                    for (comp in compilers) {
                        val compiled = comp.compile(project)
                        if (compiled.status == -1) {
                            checkedConfigurations[allTexts] = false
                            return false
                        }
                        val execResult = comp.getExecutionTime(compiled.pathToCompiled)
                        if (execResult.first.contains("Exception")) {
                            checkedConfigurations[allTexts] = false
                            return false
                        }
                        executionTimes.add(execResult.second)
                    }
                    val executionTimesDifferenceInPerc =
                        kotlin.math.abs(executionTimes.first()).toDouble() / executionTimes.last().absoluteValue
                    val execInterval = PerformanceOracle.executionConfInterval.let { it.first..it.second }
                    println("EXECUTION TIMES = $executionTimes")
                    println("EXECUTION TIME DIFFERENCE = $executionTimesDifferenceInPerc")
                    println("EXEC INTERVAL =  $execInterval")
                    if (executionTimesDifferenceInPerc !in execInterval) {
                        if (executionTimes.first() > executionTimes.last() && executionTimesDifferenceInPerc > execInterval.endInclusive) {
                            val execSigma = PerformanceOracle.executionSigma
                            val execConfInterval = PerformanceOracle.executionConfInterval
                            val executionTimeIntervalMedian = (execConfInterval.second + execConfInterval.first) / 2.0
                            val newMed = executionTimeIntervalMedian * 0.62  + executionTimesDifferenceInPerc * 0.38
                            val newInterval = newMed - execSigma to newMed + execSigma
                            PerformanceOracle.executionConfInterval = newInterval
                            executionRetValue = 1
                        } else {
                            executionRetValue = -1
                        }
                    } else {
                        executionRetValue = 0
                    }
                    println("----------------------------------------------------")
                    return when {
                        executionRetValue == 1 || compilationRetValue == 1 -> {
                            checkedConfigurations[allTexts] = true
                            true
                        }
                        compilationRetValue == -1 || executionRetValue == -1 -> {
                            checkedConfigurations[allTexts] = false
                            false
                        }
                        else -> {
                            checkedConfigurations[allTexts] = true
                            true
                        }
                    }
                }
                if (CompilerArgs.isGuidedByCoverage) {
                    if (isCoverageDecreases(project)) {
                        checkedConfigurations[allTexts] = false
                        return false
                    }
                }
                if (CompilerArgs.isMiscompilationMode) {
                    val checkRes = checkTraces(project)
                    checkedConfigurations[allTexts] = checkRes
                    return checkRes
                }
                StatisticCollector.incField("Correct programs")
                checkedConfigurations[allTexts] = true
                return true
            }
            statuses.all { it == COMPILE_STATUS.ERROR } -> {
                StatisticCollector.incField("Incorrect programs")
                checkedConfigurations[allTexts] = false
                return false
            }
        }
        //TODO!!
        checkAndGetCompilerBugs(project).forEach { BugManager.saveBug(it) }
        checkedConfigurations[allTexts] = false
        StatisticCollector.incField("Correct programs")
        return false
    }

    fun isCompilationSuccessful(project: Project): Boolean = compilers.all { it.checkCompiling(project) }

    fun compileAndGetMessage(project: Project): String = compilers.first().getErrorMessage(project)

    private fun compileAndGetStatuses(project: Project): List<COMPILE_STATUS> =
        compilers.map { it.tryToCompileWithStatus(project) }

    private fun compileAndGetStatusesWithExecutionTime(project: Project): List<Pair<COMPILE_STATUS, Long>> =
        compilers.map { it.tryToCompileWithStatusAndExecutionTime(project) }


    fun checkTraces(project: Project): Boolean {
        val copyOfProject = project.copy()
        Tracer(compilers.first(), copyOfProject).trace()
        return TracesChecker(compilers).checkBehavior(copyOfProject)
    }


    fun isCoverageDecreases(project: Project): Boolean {
        val sumCoverage = CoverageGuider.getCoverage(project, compilers)
        MyMethodBasedCoverage.methodProbes.clear()
        val k = CoverageGuider.calcKoefOfCoverageUsage(sumCoverage)
        println("k = $k")
        if (k > CoverageGuider.initCoef) CoverageGuider.initCoef = k
        return k < CoverageGuider.initCoef
    }

    private fun checkAndGetCompilerBugs(project: Project): List<Bug> {
        val res = mutableListOf<Bug>()
        compilers.forEach { compiler ->
            if (compiler.isCompilerBug(project)) {
                val msg = compiler.getErrorMessage(project)
                val type =
                    if (msg.contains("Exception while analyzing expression")) BugType.FRONTEND else BugType.BACKEND
                res.add(Bug(compiler, msg, project.copy(), type))
            }
        }
        if (res.size != 0) return res
        val compilersToStatus = compilers.map { it to it.checkCompiling(project) }
        val grouped = compilersToStatus.groupBy { it.first.compilerInfo.split(" ").first() }
        for (g in grouped) {
            if (g.value.map { it.second }.toSet().size != 1) {
                val diffCompilers =
                    g.value.groupBy { it.second }.mapValues { it.value.first().first }.values.toList()
                res.add(
                    Bug(diffCompilers, "", project.copy(), BugType.DIFFCOMPILE)
                )
            }
        }
        return res
    }

//        fun checkABI(project: Project): Pair<Int, File>? {
//            val compilers = CompilerArgs.getCompilersList()
//            if (compilers.size != 2) return null
//            val compiled = compilers.mapIndexed { index, comp ->
//                comp.pathToCompiled =
//                    comp.pathToCompiled.replace(".jar", "$index.jar")
//                comp.compile(project).pathToCompiled
//            }
//            if (compiled.any { it == "" }) return null
//            val jars = compiled.map { JarFile(it) }
//            val task =
//                JarTask(
//                    "",
//                    jars.first(),
//                    jars.last(),
//                    compilers.first().compilerInfo,
//                    compilers.last().compilerInfo,
//                    File("tmp/report.html"),
//                    checkerConfiguration {}
//                )
//            val execRes = task.execute()
//            MySummaryReport.summary.add(task.defectReport)
//            return execRes
//        }

    val additionalConditions: MutableList<(PsiFile) -> Boolean> = mutableListOf()

    private val checkedConfigurations = hashMapOf<String, Boolean>()
    private val log = Logger.getLogger("mutatorLogger")
}