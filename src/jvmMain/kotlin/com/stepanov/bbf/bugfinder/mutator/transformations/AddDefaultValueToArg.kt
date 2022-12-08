package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.mutator.transformations.tce.FillerGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.util.*

import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import kotlin.random.Random
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory


class AddDefaultValueToArg : Transformation() {

    //TODO MAYBE INIT AND REINIT SOME PROPERTIES
    override fun transform() {
        val ctx = PSICreator.analyze(file) ?: return
        val generator = RandomInstancesGenerator(file as KtFile, ctx)
        val prevParams = mutableListOf<Triple<KtExpression, String, KotlinType?>>()
        file.getAllPSIChildrenOfType<KtParameterList>()
            .filter { it.parameters.isNotEmpty() }
            .forEach { f ->
                for ((ind, valueParam) in f.parameters.withIndex()) {
                    //if (Random.getTrue(75)) continue
                    val vpType = valueParam.typeReference?.getAbbreviatedTypeOrType(ctx) ?: continue
                    if (valueParam.name == null) continue
                    prevParams.add(Triple(psiFactory.createExpression(valueParam.name!!), valueParam.name!!, vpType))
                    if (valueParam.hasDefaultValue() && Random.getTrue(50)) continue
                    if (vpType.isTypeParameter()) continue
                    val emptyConstructor = vpType.hasTypeParam()
                    val fillGenerator = FillerGenerator(file as KtFile, ctx, prevParams.getAllWithoutLast().toMutableList())
                    var value =
                        if (vpType.isNullable() && Random.getTrue(20)) {
                            "null"
                        } else if (!emptyConstructor && Random.getTrue(50)) {
                            val l = fillGenerator.getFillExpressions(valueParam to vpType)
                            l.randomOrNull()?.text
                        } else {
                            generator.generateValueOfType(vpType, withoutParams = emptyConstructor)
                        }
                    if (value == null) continue
                    if (value.trim().isEmpty()) continue
                    if (emptyConstructor) value = value.substringBefore('<') + value.substringAfterLast('>')
                    val psiValue = psiFactory.createParameter("${valueParam.text.substringBefore('=')} = $value")
                    checker.replaceNodeIfPossible(valueParam, psiValue)
                }
            }
    }


    private val randomConst = 5
}
