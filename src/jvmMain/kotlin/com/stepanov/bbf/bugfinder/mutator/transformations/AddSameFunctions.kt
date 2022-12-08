package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.reduktor.util.replaceThis

import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.BindingContext
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory
import java.util.*

class AddSameFunctions() : Transformation() {

    override fun transform() {
        if (context == null) return
        val functions = file.getAllPSIChildrenOfType<KtNamedFunction>()//.filter { Random().nextBoolean() }
        for (func in functions.filter { Random().nextBoolean() }) {
            val retType = func.typeReference?.text ?: func.getType(context).toString()
            val newRtv = generateDefValuesAsString(retType)
            if (newRtv.isEmpty()) continue
            val allParams = mutableListOf<List<Pair<KtParameter, Int>>>()
            //Adding to not create duplicates to function
            allParams.add(func.valueParameters.withIndex().map { it.value to it.index }.toList())
            for (i in 0 until Random().nextInt(10)) {
                val tmpParam = mutableListOf<Pair<KtParameter, Int>>()
                for ((index, par) in func.valueParameters.withIndex()) {
                    val type = par?.typeReference ?: continue
                    val primitiveProb = if (type.text in primitiveTypes) 80 else 20
                    val newType = generateRandomType(primitiveProb)
                    val newParam = psiFactory.createParameter("${par.name}: $newType")
                    tmpParam.add(newParam to index)
                }
                allParams.add(tmpParam)
            }
            //Now deleting first el
            allParams.removeAt(0)
            //Filter duplicates
            val filteredParams = mutableListOf<List<Pair<KtParameter, Int>>>()
            for (p in allParams) {
                if (!filteredParams.any { it.listsWithSameParams(p) }) {
                    filteredParams.add(p)
                }
            }
            for (p in filteredParams) {
                val newFunc = func.copy() as KtNamedFunction
                for ((index, par) in newFunc.valueParameters.withIndex()) {
                    val newParam = p.find { it.second == index }?.first ?: continue
                    par.replaceThis(newParam)
                }
                newFunc.initBodyByValue(psiFactory, newRtv)
                val newBlockFragment = psiFactory.createBlock(newFunc.text)
                newBlockFragment.lBrace?.delete()
                newBlockFragment.rBrace?.delete()
                checker.addNodeIfPossible(func, newBlockFragment, true)
            }
        }
    }


    private fun generateRandomType(primitiveProb: Int = 50, res: String = ""): String {
        val isPrimitive = getTrueWithProbability(primitiveProb)
        val resType = StringBuilder(res)
        if (isPrimitive) {
            resType.append(primitiveTypes[Random().nextInt(primitiveTypes.size)])
            repeat(res.count { it == '<' }) { resType.append('>') }
        } else {
            val container = containers[Random().nextInt(containers.size)]
            val newProb = Math.round(primitiveProb * 1.25).toInt()
            if (container.second == 2) {
                val first = generateRandomType(newProb)
                val second = generateRandomType(newProb)
                resType.append("${container.first}<$first,$second>")
            } else {
                val subType = generateRandomType(newProb)
                resType.append("${container.first}<$subType>")
            }
        }
        return resType.toString()
    }

    private fun List<Pair<KtParameter, Int>>.listsWithSameParams(other: List<Pair<KtParameter, Int>>): Boolean {
        return other.map { it.first.text + it.second } == this.map { it.first.text + it.second }
    }

    private fun Pair<KtParameter, Int>.sameParams(p: Pair<KtParameter, Int>) =
            p.first.text == this.first.text && p.second == this.second

    private val primitiveTypes = listOf("Int", "Double", "String", "Float", "Long", "Short", "Byte", "Char")

//    private val primitiveTypes = listOf("Int", "Double", "String", "Float", "Long", "Short", "Byte",
//            "Char", "UByte", "UShort", "ULong", "UInt")


    private val containers = listOf("List" to 1, "ArrayList" to 1, "Array" to 1, "Set" to 1, "Map" to 2,
            "Pair" to 2, "HashMap" to 2, "HashSet" to 1)

    private val context = PSICreator.analyze(checker.curFile.psiFile)//checker.curFile.ctx
}