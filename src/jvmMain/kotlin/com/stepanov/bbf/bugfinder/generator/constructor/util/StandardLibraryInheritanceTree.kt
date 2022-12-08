package com.stepanov.bbf.bugfinder.generator.constructor.util

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import java.io.File

object StandardLibraryInheritanceTree {

    private val map: Map<String, List<String>>

    init {
        val file = File(CompilerArgs.pathToStdLibScheme)
        map =
            file.readText()
                .split("\n")
                .map { it.split(" = ").let { it.first() to it.last().let { it.substring(1, it.length - 1) }.split(", ") } }
                .toMap()
    }

    fun getSubtypesOf(type: String): List<String> = map.entries.filter { it.value.contains(type) }.map { it.key }

    fun isContainer(type: String): Boolean {
        if (type.contains("Map") || type.contains("Pair")) return true
        return map[type]?.contains("Iterable") ?: false
    }

}