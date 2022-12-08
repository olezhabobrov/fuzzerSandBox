//package com.stepanov.bbf.bugfinder.generator.subjectgenerator
//
//import com.intellij.psi.PsiElement
//import org.jetbrains.kotlin.psi.KtPrimaryConstructor
//import java.util.*
//
//class Constructor(typeParam: String = "", numOfParams: Int = 5) : Expression() {
//
//    override val value: KtPrimaryConstructor
//
//    init {
//        value = generateConstructor(numOfParams, typeParam)
//    }
//
//    private fun generateConstructor(numOfParams: Int, typeParam: String): KtPrimaryConstructor {
//        val params = mutableListOf<Parameter>()
//        if (typeParam.isNotEmpty() && Random().nextBoolean()) params.add(Parameter(typeParam))
//        while (params.size != numOfParams) params.add(Parameter(""))
//        return factory.createPrimaryConstructor("(${params.joinToString(",")})")
//    }
//
//    override fun toString(): String {
//        return value.text
//    }
//
//}