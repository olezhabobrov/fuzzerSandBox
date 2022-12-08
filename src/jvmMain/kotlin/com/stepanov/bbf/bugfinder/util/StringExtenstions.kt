package com.stepanov.bbf.bugfinder.util

fun String.splitWithoutRemoving(regex: Regex): List<String> {
    val ranges = regex.findAll(this).map { it.range }.toList().flatMap { listOf(it.first, it.last + 1) }.toMutableList()
    if (ranges.isEmpty()) return listOf(this)
    if (ranges.first() != 0) ranges.add(0, 0)
    if (ranges.last() != this.length) ranges.add(this.length)
    return ranges.zipWithNext().map { this.substring(it.first, it.second) }
}

fun String.erase(str: String) =
    this.split(str).joinToString("")