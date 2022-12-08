package com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators

import com.stepanov.bbf.bugfinder.util.ModifierSets
import com.stepanov.bbf.bugfinder.util.getTrue
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import kotlin.random.Random

class RandomObjectGenerator(file: KtFile, ctx: BindingContext, depth: Int = 0)
    : RandomClassGenerator(file, ctx, depth) {

    override val classWord: String = "object"

    override fun generateModifiers(): MutableList<String> {
        val visibilityModifiers =
            ModifierSets.VISIBILITY_MODIFIER.types.toList().map { it.toString() }.filter { it != "protected" }
        val visibilityModifier = if (Random.getTrue(20)) "" else visibilityModifiers.random()
        return mutableListOf(visibilityModifier)
    }

    override fun generateTypeParams(withModifiers: Boolean): List<String> = listOf()

    override fun generateConstructor(): List<String> = listOf()

    override fun generateAnnotations(): List<String> = listOf()
}