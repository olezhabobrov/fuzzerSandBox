package com.stepanov.bbf.bugfinder.util

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtFile

object DefaultKotlinTypes {
    private val rtg = RandomTypeGenerator

    init {
        val project = Project.createFromCode("""
            fun box() {
                return "OK"
            }
        """.trimIndent())
        val ktFile = project.files.first().psiFile as KtFile
        val ctx = PSICreator.analyze(ktFile, project)!!
        rtg.setFileAndContext(ktFile, ctx)
    }

    val unitType = rtg.generateType("Unit")!!
    val anyType = rtg.generateType("Any")
    val nullableAny = rtg.generateType("Any?")
    val intType = rtg.generateType("Int")!!

}