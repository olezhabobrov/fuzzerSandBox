package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.exitProcess


object PerformanceOracle {

    lateinit var project: Project
    lateinit var compilers: List<CommonCompiler>
    lateinit var compilationConfInterval: Pair<Double, Double>
    var compilationSigma: Double = -1.0
    lateinit var executionConfInterval: Pair<Double, Double>
    var executionSigma: Double = -1.0

    fun init(project: Project, compilers: List<CommonCompiler>) {
        this.project = project
        this.compilers = compilers
        if (compilers.size != 2) return
        val firstCompiler = compilers.first()
        val secondCompiler = compilers.last()
        println("COMP INITIALIZATION")
        val compilationTimes =
            Array(10) { 0 }.map {
                firstCompiler.tryToCompileWithStatusAndExecutionTime(project).second to
                        secondCompiler.tryToCompileWithStatusAndExecutionTime(project).second
            }.drop(3)
        println(compilationTimes)
        val diffOfCompilationTimes =
            compilationTimes.map { abs(it.first.toDouble() / it.second) }
        println(diffOfCompilationTimes)
        val averageCompilationDeviation = diffOfCompilationTimes.average()
        val compilationVariance = diffOfCompilationTimes.map { (it - averageCompilationDeviation).pow(2) }.average()
        compilationSigma = sqrt(compilationVariance) * 3
        compilationConfInterval =
            averageCompilationDeviation - compilationSigma to averageCompilationDeviation + compilationSigma
        println("COMP INITIALIZATION DONE")
        println("EXECUTUION INITIALIZATION")
        val executionTimes =
            compilers.map { compiler ->
                val compiled = compiler.compile(project)
                Array(5) { 0 }.map {
                    compiler.getExecutionTime(compiled.pathToCompiled).second
                }
            }
        executionTimes.forEach { println(it) }
        val diffOfExecutionTimes =
            executionTimes.first()
                .mapIndexed { index, l -> l to executionTimes.last()[index] }
                .map { abs(it.first.toDouble() / it.second) }
        println(diffOfExecutionTimes)
        val averageExecutionDeviation = diffOfExecutionTimes.average()
        val executionVariance = diffOfExecutionTimes.map { (it - averageExecutionDeviation).pow(2) }.average()
        executionSigma = sqrt(executionVariance) * 2
        executionConfInterval = averageExecutionDeviation - executionSigma to averageExecutionDeviation + executionSigma
        println("EXECUTUION INITIALIZATION DONE")
    }

    fun initWithAverageResults(project: Project, compilers: List<CommonCompiler>): Pair<Double, Double>? {
        this.project = project
        this.compilers = compilers
        if (compilers.size != 2) return null
        val firstCompiler = compilers.first()
        val secondCompiler = compilers.last()
        println("COMP INITIALIZATION")
        val compilationTimes =
            Array(10) { 0 }.map {
                firstCompiler.tryToCompileWithStatusAndExecutionTime(project).second to
                        secondCompiler.tryToCompileWithStatusAndExecutionTime(project).second
            }.drop(3)
        println(compilationTimes)
        val diffOfCompilationTimes =
            compilationTimes.map { abs(it.first.toDouble() / it.second) }
        println(diffOfCompilationTimes)
        val averageCompilationDeviation = diffOfCompilationTimes.average()
        val compilationVariance = diffOfCompilationTimes.map { (it - averageCompilationDeviation).pow(2) }.average()
        compilationSigma = sqrt(compilationVariance) * 3
        compilationConfInterval =
            averageCompilationDeviation - compilationSigma to averageCompilationDeviation + compilationSigma
        println("COMP INITIALIZATION DONE")
        println("EXECUTUION INITIALIZATION")
        var prevRes = 0.0
        var averageExecutionDeviation = 0.0
        repeat(10) { ind ->
            println("NUMBER OF EXECUTIONS: = ${10.0.pow(ind).toInt()}")
            val executionTimes =
                compilers.map { compiler ->
                    val compiled = compiler.compile(project, 10.0.pow(ind).toInt())
                    Array(3) { 0 }.map {
                        compiler.getExecutionTime(compiled.pathToCompiled).second
                    }
                }
            executionTimes.forEach { println(it) }
            val diffOfExecutionTimes =
                executionTimes.first()
                    .mapIndexed { index, l -> l to executionTimes.last()[index] }
                    .map { abs(it.first.toDouble() / it.second) }
            println(diffOfExecutionTimes)
            prevRes = averageExecutionDeviation
            averageExecutionDeviation = diffOfExecutionTimes.average()
            val executionVariance = diffOfExecutionTimes.map { (it - averageExecutionDeviation).pow(2) }.average()
            executionSigma = sqrt(executionVariance) * 2
            executionConfInterval =
                averageExecutionDeviation - executionSigma to averageExecutionDeviation + executionSigma
            val maxExecutionTime = executionTimes.maxOf { it.maxOf { it } }
            if (maxExecutionTime in 1000..4500 || ind == 9) {
                println("EXECUTUION INITIALIZATION DONE")
                return averageCompilationDeviation to averageExecutionDeviation
            } else if (maxExecutionTime > 4500) {
                return averageCompilationDeviation to prevRes
            }
        }
        return averageCompilationDeviation to averageExecutionDeviation
        val executionTimes =
            compilers.map { compiler ->
                val compiled = compiler.compile(project)
                Array(5) { 0 }.map {
                    compiler.getExecutionTime(compiled.pathToCompiled).second
                }
            }
        executionTimes.forEach { println(it) }
        val diffOfExecutionTimes =
            executionTimes.first()
                .mapIndexed { index, l -> l to executionTimes.last()[index] }
                .map { abs(it.first.toDouble() / it.second) }
        println(diffOfExecutionTimes)
        averageExecutionDeviation = diffOfExecutionTimes.average()
        val executionVariance = diffOfExecutionTimes.map { (it - averageExecutionDeviation).pow(2) }.average()
        executionSigma = sqrt(executionVariance) * 2
        executionConfInterval = averageExecutionDeviation - executionSigma to averageExecutionDeviation + executionSigma
        println("EXECUTUION INITIALIZATION DONE")
        return averageCompilationDeviation to averageExecutionDeviation
    }


}