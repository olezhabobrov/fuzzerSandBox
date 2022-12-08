package com.stepanov.bbf.bugfinder.mutator.projectTransformations

import com.intellij.lang.ASTNode
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory
import com.stepanov.bbf.bugfinder.mutator.transformations.ChangeRandomASTNodes
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.util.getAllChildrenNodes
import com.stepanov.bbf.bugfinder.util.replaceThis
import com.stepanov.bbf.reduktor.parser.PSICreator
import kotlin.random.Random

class ShuffleNodes : Transformation() {

    override fun transform() = TODO()
//        val numOfSwaps = Random.nextInt(numOfSwaps.first, numOfSwaps.second)
//        val othFiles = checker.otherFiles!!.texts.map { PSICreator.getPSIForText(it) }
//        val files = listOf(file) + othFiles
//        for (i in 0 until numOfSwaps) {
//            val children = files.flatMap { it.node.getAllChildrenNodes() }
//            ChangeRandomASTNodes.swapRandomNodes(children, psiFactory, files)
//        }
//        checker.otherFiles = Project(othFiles)
//   }

    val numOfSwaps = 500 to 1000

}