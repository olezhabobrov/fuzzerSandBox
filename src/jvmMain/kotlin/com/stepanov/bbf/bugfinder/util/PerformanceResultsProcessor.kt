package com.stepanov.bbf.bugfinder.util

import java.io.File
import kotlin.system.exitProcess

object PerformanceResultsProcessor {

    fun main() {
        val file = File("/home/zver/IdeaProjects/kotlinBugs/performance/tmp/res.txt")
        val text = file.readText()
        val t1 = text.split("\n").filter { it.contains("FILE.kt") || it.contains("PROJECT.kt") || it.contains("AVERAGE") }
            .dropLast(1)
        val results = t1.filterIndexed { i, s ->
            if (s.contains("FILE.kt") || s.contains("PROJECT.kt")) {
                t1[i + 1].contains("AVERAGE")
            } else true
        }
        val res = mutableListOf<Triple<String, Double, Double>>()
        for (i in results.indices step 3) {
            if (i >= results.size - 2) break
            val name = results[i]
            val comp = results[i + 1].substringAfter("= ").toDouble()
            val exec = results[i + 2].substringAfter("= ").toDouble()
            res.add(Triple(name, comp, exec))
        }
        var i = 0
        res.sortedByDescending { it.third }.map { it }.take(50).forEach(::println)
        exitProcess(0)
        for (f in res.sortedByDescending { it.third }.map { it.first }.take(50)) {
            val b1 = text.split("\n").indexOfFirst { it.contains(f) }
            val neededText = text.split("\n").drop(b1).takeWhile { !it.contains("-------") }
            val needFile = File("/home/zver/IdeaProjects/kotlinBugs/performance/${neededText.first()}")
            val needFileText = needFile.readText()
            val execInfo = neededText.reversed().take(7)
            val numberOfExecutions = execInfo[6].substringAfter("= ")
            val execDeviation = execInfo[0].substringAfter("= ")
            val c1 = execInfo[5]
            val c2 = execInfo[4]
            val diffs = execInfo[3]
            val prefix = """
    // Average diff: $execDeviation
    // JVM exec times: $c1
    // JVM old exec times: $c2
    // Diff: $diffs
""".trimIndent()
            val mainFun = """
fun main() {
    repeat($numberOfExecutions) { box() }
}
""".trimIndent()
            val finalText = "$prefix\n$needFileText\n$mainFun"
            File("/home/zver/IdeaProjects/kotlinBugs/performanceResults/${needFile.name}").writeText(finalText)
        }
    }

}