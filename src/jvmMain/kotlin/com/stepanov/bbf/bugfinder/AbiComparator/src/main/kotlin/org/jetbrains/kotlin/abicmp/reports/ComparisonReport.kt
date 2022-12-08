package org.jetbrains.kotlin.abicmp.reports

import java.io.PrintWriter

interface ComparisonReport {
    fun isEmpty(): Boolean
    fun write(output: PrintWriter)
}

fun ComparisonReport.isNotEmpty() = !isEmpty()