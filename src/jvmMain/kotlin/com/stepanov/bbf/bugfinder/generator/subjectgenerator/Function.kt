//package com.stepanov.bbf.bugfinder.generator.subjectgenerator
//
//import com.intellij.psi.PsiElement
//import com.stepanov.bbf.bugfinder.util.getRandomVariableName
//import org.jetbrains.kotlin.psi.KtFunction
//import java.lang.StringBuilder
//import java.util.*
//
//class Function : Expression() {
//    override val value: KtFunction
//
//    init {
//        value = generateFunc()
//    }
//
//    fun generateFunc(): KtFunction {
//        val res = StringBuilder()
//        //DO smth with modifier
//        val modifiers = listOf(
//            "tailrec", "operator", "infix", "external", "lateinit",
//            "override", "open", "final", "abstract", "private", "public", "protected", "internal", "const"
//        )
//        val numOfParams = 2
//        res.append("fun ${Random().getRandomVariableName(5)} (${(0..numOfParams - 1).map { Parameter() }.joinToString(",")}) {}")
//        println("res = $res")
//        return factory.createFunction(res.toString())
//    }
//
//    override fun toString(): String = value.text
//
//}