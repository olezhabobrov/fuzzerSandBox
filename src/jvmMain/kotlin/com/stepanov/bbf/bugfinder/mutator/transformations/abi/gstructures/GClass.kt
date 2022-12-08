package com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

data class GClass(
    var annotations: List<String> = listOf(),
    var imports: MutableList<String> = mutableListOf(),
    override var modifiers: MutableList<String> = mutableListOf(),
    var classWord: String = "",
    var name: String = "",
    var typeParams: List<String> = listOf(),
    var constructorWord: String = "",
    var constructorArgs: List<String> = listOf(),
    var supertypes: List<String> = listOf(),
    var body: String = ""
): GStructure() {

    override fun toString(): String {
        val a = annotations.joinToString("\n", postfix = "\n")
        val m = modifiers.let { if (it.all { it.isEmpty() }) "" else it.joinToString(" ") }
        val c =
            if (constructorArgs.isEmpty()) ""
            else constructorArgs.joinToString(prefix = "(", postfix = ")")
        val t =
            if (typeParams.isEmpty()) ""
            else typeParams.joinToString(prefix = "<", postfix = "> ")
        val s =
            if (supertypes.isEmpty()) ""
            else supertypes.joinToString(prefix = ": ")
        val b =
            if (body.isEmpty()) ""
            else Factory.psiFactory.createBlock(body).text
        return "$a$m $classWord $name $t $constructorWord$c$s$b"
    }

    companion object {
        fun fromPsi(klass: KtClassOrObject): GClass {
            val gClass = GClass()
            gClass.classWord =
                if (klass is KtObjectDeclaration) "object"
                else if (klass is KtClass && klass.isInterface()) "interface"
                else "class"
            gClass.name = klass.name ?: ""
            gClass.annotations = klass.annotations.let {
                if (it.isEmpty()) listOf() else it.map { it.text }
            }.toMutableList()
            gClass.modifiers = klass.modifierList?.text?.split(" ")?.toMutableList() ?: mutableListOf()
            gClass.typeParams = klass.typeParameters.let { if (it.isEmpty()) listOf() else it.map { it.text } }
            gClass.constructorWord =
                klass.primaryConstructor?.let { if (it.isPrivate()) "private constructor" else "" } ?: ""
            gClass.constructorArgs = klass.primaryConstructor?.valueParameters
                ?.let { if (it.isEmpty()) listOf() else it.map { it.text } } ?: listOf()
            gClass.supertypes = klass.superTypeListEntries.let {
                if (it.isEmpty()) listOf() else it.map { it.text }
            }
            gClass.body =
                when {
                    klass.body == null -> ""
                    klass.body!!.text.trim().startsWith("{") -> klass.body!!.text.substringAfter('{').substringBeforeLast('}')
                    else -> klass.body!!.text
                }
            return gClass
        }
    }

    fun toPsi(): PsiElement? =
        try {
            if (isObject()) Factory.psiFactory.createObject(toString())
            else Factory.psiFactory.createClass(toString())
        } catch (e: Exception) {
            null
        }

    fun isObject() = classWord == "object"
    fun isFunInterface() = isInterface() && modifiers.contains("fun")
    fun isInner() = modifiers.contains("inner")
    fun isAnnotation() = modifiers.contains("annotation")
    fun isEnum() = modifiers.contains("enum")
    fun isData() = modifiers.contains("data")
    fun isInterface() = classWord == "interface"
    fun isSealed() = modifiers.contains("sealed")
    fun hasPrivateConstructor() = constructorWord.contains("private")
}