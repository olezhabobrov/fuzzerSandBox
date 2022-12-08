package com.stepanov.bbf.bugfinder.mutator.transformations

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory

import com.stepanov.bbf.bugfinder.util.generateDefValuesAsString
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.parser.PSICreator

//TODO Add for map!!
class ReinitProperties : Transformation() {
    override fun transform() {
        file.getAllPSIChildrenOfType<KtProperty>().forEach {
            val type =
                    if (it.hasInitializer()) {
                        getTypeFromInitializer(it.initializer!!)
                    } else {
                        it.typeReference?.text ?: return@forEach
                    }
            if (type.isEmpty()) return@forEach
            val newValue = generateDefValuesAsString(type)
            if (newValue.isEmpty()) return@forEach
            val newProp = it.copy() as KtProperty
            newProp.initializer = psiFactory.createExpression(newValue)
            checker.replaceNodeIfPossible(it, newProp)
        }
    }

    private fun getTypeFromInitializer(expr: KtExpression, resultingType: String = ""): String {
        val initType = context?.getType(expr).toString()
        return if (initType == "null") {
            if (generateDefValuesAsString(expr.text).isNotEmpty())
                concatType(expr.text, resultingType)
            else {
                val constructor = constructorsToTypes.keys.find { expr.text.startsWith(it) } ?: return ""
                val value = constructorsToTypes[constructor]!!
                getTypeFromInitializer(expr.getAllPSIChildrenOfType<KtExpression>().component2(), concatType(value, resultingType))
            }
        } else
            concatType(initType, resultingType)
    }

    private fun concatType(type: String, res: String): String =
            when {
                res.isEmpty() -> type
                res.contains('>') -> "${res.substringBeforeLast('>')}<$type>>"
                else -> "$res<$type>"
            }

    private val constructorsToTypes = mapOf("arrayListOf" to "ArrayList", "listOf" to "List",
            "setOf" to "Set", "arrayOf" to "Array")

    private val context = PSICreator.analyze(checker.curFile.psiFile)
}