package com.stepanov.bbf.bugfinder.mutator.transformations.abi

import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import kotlin.random.Random

object GeneratorFactory {
    lateinit var file: KtFile
    lateinit var ctx: BindingContext

    fun createTopLevelGenerators(): List<DSGenerator> =
        with(mutableListOf<DSGenerator>()) {
            add(RandomPropertyGenerator(file, ctx))
            add(RandomFunctionGenerator(file, ctx))
            add(RandomClassGenerator(file, ctx))
            add(RandomInterfaceGenerator(file, ctx))
            add(RandomObjectGenerator(file, ctx))
            this
        }

//    fun getRandomGenerator(): DSGenerator =
//        when (Random.nextInt(0, 3)) {
//            0 -> RandomObjectGenerator(file, ctx)
//            1 -> RandomInterfaceGenerator(file, ctx)
//            else -> RandomClassGenerator(file, ctx)
//        }
    fun getRandomGenerator(): DSGenerator =
        when (Random.nextInt(0, 10)) {
            //TODO temporary filter because of noise
            //0 -> RandomPropertyGenerator(file, ctx = ctx)
            1 -> RandomFunctionGenerator(file, ctx)
            2, 3 -> RandomInterfaceGenerator(file, ctx)
            4, 5 -> RandomObjectGenerator(file, ctx)
            else -> RandomClassGenerator(file, ctx)
        }

    fun update(file: KtFile, ctx: BindingContext) =
        run {
            this.file = file
            this.ctx = ctx
            true
        }

}