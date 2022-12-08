package com.stepanov.bbf.bugfinder.mutator.projectTransformations

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import kotlin.system.exitProcess


//TODO finish
class ProjectSplitter : Transformation() {


    override fun transform() {
        val project = checker.project
        val newFiles = generateSequence { Factory.psiFactory.createFile("") }.take(NUM_OF_FILES).toList()
//        val splitProject = Project(
//            project.configuration,
//            newFiles.mapIndexed { index, ktFile -> BBFFile("$index.kt", ktFile) },
//            project.language
//        )
        val topLevelObjects =
            project.files
                .flatMap { it.psiFile.getAllChildren().filter { it.isTopLevelKtOrJavaMember() } }
                .toMutableList()
        repeat(topLevelObjects.size) {
            val randomEl = topLevelObjects.randomOrNull() ?: return@repeat
            newFiles.random().addAtTheEnd(randomEl)
            topLevelObjects.remove(randomEl)
        }
        exitProcess(0)
    }

    val NUM_OF_FILES = 3
}