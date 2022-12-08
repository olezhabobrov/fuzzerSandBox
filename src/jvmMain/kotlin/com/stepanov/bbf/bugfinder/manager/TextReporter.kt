package com.stepanov.bbf.bugfinder.manager

import java.io.File

object TextReporter : Reporter {

    override fun dump(bugs: List<Bug>) {
        val file = File(ReportProperties.getPropValue("PATH_TO_TEXT_FILE"))
        //Sorting
        val sortedBugs = bugs.sortedWith(Comparator { o1, o2 -> o1.compareTo(o2) })
        val bugsToStr = sortedBugs.joinToString(separator = "\n\n\n")
        {
            """*******************************************************************
                |Compiler: ${it.compilerVersion}
            |Type: ${it.type}
            |CrashingCode: ${it.crashedProject}
            |Message: ${it.msg}
            |*******************************************************************""".trimMargin()
        }
        file.appendText(bugsToStr)
    }


}