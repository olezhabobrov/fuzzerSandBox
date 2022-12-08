package com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GStructure
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import com.stepanov.bbf.bugfinder.util.ModifierSets
import kotlin.random.Random

abstract class AbstractClassGenerator(
    open val file: KtFile,
    open val ctx: BindingContext,
    open val depth: Int = 0
) : DSGenerator(file, ctx) {

    var gClass: GClass = GClass()
    abstract val classWord: String

    abstract fun generateModifiers(): MutableList<String>
    abstract fun generateConstructor(): List<String>
    abstract fun generateSupertypes(): List<String>
    abstract fun generateAnnotations(): List<String>


    override fun simpleGeneration(): PsiElement? {
        with (gClass) {
            classWord = this@AbstractClassGenerator.classWord
            annotations = generateAnnotations()
            modifiers = generateModifiers()
            name = Random.getRandomVariableName(3).capitalize()
            typeParams = generateTypeParams(true)
            constructorArgs = generateConstructor()
            supertypes = generateSupertypes()
            body = ClassBodyGenerator(file, ctx, gClass, depth + 1).generateBodyAsString()
        }
        return gClass.toPsi()
    }

    override fun partialGeneration(initialStructure: GStructure): PsiElement? {
        gClass = initialStructure as? GClass ?: return null
        with (gClass) {
            if (classWord.isEmpty()) classWord = this@AbstractClassGenerator.classWord
            if (annotations.isEmpty()) annotations = generateAnnotations()
            if (modifiers.isEmpty()) modifiers = generateModifiers()
            if (name.isEmpty()) name = Random.getRandomVariableName(3).capitalize()
            if (typeParams.isEmpty()) typeParams = generateTypeParams(true)
            if (constructorArgs.isEmpty()) constructorArgs = generateConstructor()
            if (supertypes.isEmpty()) supertypes = generateSupertypes()
            if (body.isEmpty()) body = ClassBodyGenerator(file, ctx, gClass, depth + 1).generateBodyAsString()
        }
        return gClass.toPsi()
    }

    override fun beforeGeneration() {
        randomTypeGenerator.minVisibility = "public"
    }

    override fun afterGeneration(psi: PsiElement) {
        //Visibility modifier
        val mod = generateVisibilityModifier(randomTypeGenerator.minVisibility)
        val ktModifier =
            when (mod) {
                "private" -> KtTokens.PRIVATE_KEYWORD
                "internal" -> KtTokens.INTERNAL_KEYWORD
                "public" -> KtTokens.PUBLIC_KEYWORD
                else -> KtTokens.PROTECTED_KEYWORD
            }
        val ktClass = psi as? KtClassOrObject ?: return
        ktClass.addModifier(ktModifier)
        val modList = ModifierSets.VISIBILITY_MODIFIER.types.toList().map { it.toString() }
        gClass.modifiers.replaceAll { if (it in modList) mod else it }
        randomTypeGenerator.minVisibility = mod
    }


}