package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomPropertyGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class AddPropertiesToClass : Transformation() {


    override fun transform() {
        repeat(RAND_CONST) {
            val ctx = PSICreator.analyze(file) ?: return
            val randomKlass = file.getAllPSIChildrenOfType<KtClass>().randomOrNull() ?: return
            val gClass = GClass.fromPsi(randomKlass)
            val (genProp, rType) = RandomPropertyGenerator(file as KtFile, ctx, gClass).generateInterestingProperty(randomKlass)
                ?: return@repeat
            var r = randomKlass.addPsiToBody(genProp) ?: return@repeat
            if (r is KtBlockExpression) {
                val newR = r.getAllPSIChildrenOfType<KtProperty>().firstOrNull()
                if (newR != null) r = newR
            }
            val res = checker.checkCompiling()
            if (!res) {
                if (r is KtProperty) r.addModifier(KtTokens.OVERRIDE_KEYWORD)
                if (r is KtNamedFunction) r.addModifier(KtTokens.OVERRIDE_KEYWORD)
                if (!checker.checkCompiling()) {
                    if (r is KtProperty) r.addModifier(KtTokens.ABSTRACT_KEYWORD)
                    if (r is KtNamedFunction) r.addModifier(KtTokens.ABSTRACT_KEYWORD)
                    if (!checker.checkCompiling()) {
                        if (r is KtProperty && rType != null) {
                            val expr = RandomInstancesGenerator(file as KtFile, ctx).generateValueOfType(rType)
                            val psiExpr = Factory.psiFactory.createExpression(expr)
                            r.initializer = psiExpr
                            if (!checker.checkCompiling()) {
                                r.delete()
                            }
                        } else {
                            r.delete()
                        }
                    }
                }
            }
            val newKlass = Factory.psiFactory.createClass(randomKlass.text)
            randomKlass.replaceThis(newKlass)
        }
    }

    private val RAND_CONST = 50
}
