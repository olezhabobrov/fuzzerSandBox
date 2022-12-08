package com.stepanov.bbf.bugfinder.mutator.transformations

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

import com.stepanov.bbf.bugfinder.util.getAllChildrenNodes
import com.stepanov.bbf.bugfinder.util.getRandomBoolean
import java.util.*

class AddPossibleModifiers : Transformation() {

    override fun transform() {
        val values = file.node.getAllChildrenNodes()
                .asSequence()
                .filter { it.elementType == KtNodeTypes.CLASS || it.elementType == KtNodeTypes.PROPERTY
                        || it.elementType == KtNodeTypes.FUN }
                .filter { getRandomBoolean(4) }
                .toList()
        for (i in 0..randomConstant) {
            values.forEach {
                val curWorkingList =
                    when (it.elementType) {
                        KtNodeTypes.CLASS -> possibleClassModifiers
                        KtNodeTypes.PROPERTY -> possiblePropertyModifiers
                        else -> possibleFunctionModifiers
                    }
                val el =
                    when (it.elementType) {
                        KtNodeTypes.CLASS -> it.psi as KtClass
                        KtNodeTypes.PROPERTY -> it.psi as KtProperty
                        else -> it.psi as KtFunction
                    }
                val num = Random().nextInt(curWorkingList.size)
                val keyword = KtTokens.MODIFIER_KEYWORDS_ARRAY.find { it.value == curWorkingList[num] } ?: return@forEach
                if (el.hasModifier(keyword)) return@forEach
                el.addModifier(keyword)
                if (!checker.checkCompilingWithBugSaving(file))
                    el.removeModifier(keyword)
            }
        }
    }

    private val possibleClassModifiers = listOf("private", "protected", "internal", "public", "open", "inner", "sealed",
            "data", "abstract", "enum")

    private val possiblePropertyModifiers = listOf("lateinit", "override", "open", "final", "abstract", "private",
            "public", "protected", "internal", "const")

    private val possibleFunctionModifiers = listOf("tailrec", "operator", "infix", "external") //"suspend"

    private val randomConstant = 25

}