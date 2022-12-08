package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomFunctionGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.util.replaceThis
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class AddMethodToClass : Transformation() {
    override fun transform() {
        repeat(MAGIC_CONST) {
            val ctx = PSICreator.analyze(file) ?: return
            val ktFile = file as? KtFile ?: return
            val randomClass = file.getAllPSIChildrenOfType<KtClassOrObject>().randomOrNull() ?: return
            val randomGClass = GClass.fromPsi(randomClass)
            val functionGenerator = RandomFunctionGenerator(ktFile, ctx, randomGClass)
            val generatedFunction = functionGenerator.generate() as? KtNamedFunction ?: return@repeat
            if (randomClass.body == null) {
                randomClass.addPsiToBody(generatedFunction)
                if (!checker.checkCompiling())
                    randomClass.body?.replaceThis(Factory.psiFactory.createWhiteSpace())
            } else {
                val addedFunc = randomClass.addPsiToBody(generatedFunction)
                if (!checker.checkCompiling())
                    addedFunc?.replaceThis(Factory.psiFactory.createWhiteSpace())
            }
        }
    }

    private val MAGIC_CONST = 10
}