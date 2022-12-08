package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElementFactory
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomClassGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomInterfaceGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.util.addToTheTop
import com.stepanov.bbf.bugfinder.util.getTrue
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import kotlin.random.Random
import kotlin.system.exitProcess

class AddRandomClass : Transformation() {

    private val ktFile = file as KtFile
    private val ctx = PSICreator.analyze(ktFile)

    override fun transform() {
        if (ctx == null) return
        val parentClass = ktFile.getAllPSIChildrenOfType<KtClassOrObject>().randomOrNull()
        val klassGenerator =
            if (parentClass != null && Random.getTrue(50)) {
                val parentClassAsGClass = GClass.fromPsi(parentClass)
                RandomClassGenerator(ktFile, ctx, 0, parentClassAsGClass)
            } else {
                RandomClassGenerator(ktFile, ctx)
            }
        val newClass = klassGenerator.generate() ?: return
        val addedClass = ktFile.addToTheTop(newClass)
        if (!checker.checkCompiling()) {
            addedClass.delete()
        }
    }
}