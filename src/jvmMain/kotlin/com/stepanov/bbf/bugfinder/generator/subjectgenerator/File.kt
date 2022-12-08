package com.stepanov.bbf.bugfinder.generator.subjectgenerator

import org.jetbrains.kotlin.psi.KtFile
import java.lang.StringBuilder

class File: Expression() {
    override val value: KtFile

    init {
        value = factory.createFile("")
    }

    fun generateFile(): KtFile {
        val resFile = StringBuilder()
        return factory.createFile(resFile.toString())
    }
}