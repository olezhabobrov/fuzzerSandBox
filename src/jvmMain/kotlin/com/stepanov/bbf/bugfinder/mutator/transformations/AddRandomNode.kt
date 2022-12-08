package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.random.Random

class AddRandomNode : Transformation() {
    override fun transform() {
        val randConst = Random.nextInt(numOfTries.first, numOfTries.second)
        val filteredNodes = file.node.getAllChildrenNodes().filter { it.elementType !in NodeCollector.excludes }
        val nodeDB = File("database.txt").bufferedReader().lines().toArray().toList()
        log.debug("Trying to add some nodes $randConst times")
        println("Trying to add some nodes $randConst times")
        //From same file
        var i = 0
        while (i != 3000) {
            println("i = $i")
            val randomNode = filteredNodes.randomOrNull()?.psi?.copy() ?: continue
            val placeToInsert = file.getAllChildren().randomOrNull() ?: continue
            val whiteSpace = if (Random.nextBoolean()) " " else "\n"
            val fileBackup = file.copy() as KtFile
            placeToInsert.addAfterThisWithWhitespace(randomNode, whiteSpace).let {
                if (!checker.checkCompiling()) {
                    checker.curFile.changePsiFile(fileBackup, genCtx = false)
                }
                ++i
            }
//            try {
//                if (Random.nextBoolean()) {
//                    val whiteSpace = "\n"
//                    placeToInsert.addAfterWithWhitespace(randomNode, whiteSpace).let {
//                        if (!checker.checkCompiling()) {
//                            it.first.delete()
//                            //it.second?.delete()
//                            //it.third?.delete()
//                        }
//                    }
//                } else {
//                    placeToInsert.add(randomNode).let {
//                        if (!checker.checkCompiling()) it.delete()
//                    }
//                }
//            } catch (e: Throwable) {
//                println("EXCEPTION")
//            }
//            continue
        }
//        println("RES = ${file.text}")
//        exitProcess(0)
//        for (i in 0..randConst / 2) {
//            val nodeToAdd = nodeDB.random().dropLast(1).takeLastWhile { it != '[' }.split(", ").random()
//        }
//        exitProcess(0)
    }

    private val numOfTries = if (checker.project.files.size == 1) 50 to 500 else 1000 to 2000
}