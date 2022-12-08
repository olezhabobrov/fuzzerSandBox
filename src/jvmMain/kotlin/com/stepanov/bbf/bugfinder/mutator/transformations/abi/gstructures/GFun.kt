package com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.getRandomVariableName
import kotlin.random.Random

data class GFunction(
    override var modifiers: MutableList<String> = mutableListOf(),
    var typeArgs: List<String> = listOf(),
    var extensionReceiver: String = "",
    var name: String = Random.getRandomVariableName(5),
    var args: List<String> = listOf(),
    var rtvType: String = "",
    var body: String = ""
): GStructure() {
    fun toPsi(): PsiElement {
        val m = modifiers.let { if (it.all { it.isEmpty() }) "" else it.joinToString(" ") }
        val e = if (extensionReceiver.isEmpty()) "" else " $extensionReceiver."
        val sta = if (typeArgs.isEmpty()) "" else typeArgs.joinToString(prefix = "<", postfix = "> ")
        val strArgs = args.joinToString()
        return Factory.psiFactory.createFunction("$m fun $sta $e$name($strArgs): $rtvType $body")
    }
}