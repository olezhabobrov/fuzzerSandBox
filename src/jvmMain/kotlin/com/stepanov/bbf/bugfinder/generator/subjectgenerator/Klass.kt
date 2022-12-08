//package com.stepanov.bbf.bugfinder.generator.subjectgenerator
//
//import com.intellij.psi.PsiElement
//import com.stepanov.bbf.bugfinder.util.getRandomVariableName
//import com.stepanov.bbf.bugfinder.util.replaceThis
//import com.stepanov.bbf.reduktor.util.generateDefValuesAsString
//import org.jetbrains.kotlin.psi.KtPrimaryConstructor
//import java.lang.StringBuilder
//import java.util.*
//
//abstract class Klass() : Expression() {
//
//    fun generate(): PsiElement {
//        //Mb type with bound
//        val typeParam = /*if (Random().nextBoolean()) "<T>" else*/ ""
//        val ident = Random().getRandomVariableName()
//        val constructor = generateConstructor(typeParam)?.let { "$it" } ?: ""
//        //Delegation
//        //Type parameter bounds? () where T: Number
//        val body = generateBody()
//        return factory.createClass("$modifier ${ident.capitalize()}$typeParam $constructor {\n$body\n}")
//    }
//
//    open fun generateConstructor(typeParam: String): Constructor? =
//        Constructor(typeParam.substringAfter('<').substringBefore('>'))
//
//    open fun generateBody(): String {
//        val res = StringBuilder()
//        //Generate properties
//        val numOfProps = 2
//        repeat(numOfProps) { res.append("${Property("", true)}\n") }
//        //Generate funcs
//        val numOfFuncs = 2
//        //repeat(numOfFuncs) { res.append()}
//        return res.toString()
//    }
//
//    open val possibleModifiers = listOf("private", "protected", "internal", "public", "open")
//    open val modifier = "class"
//
//    override val value: PsiElement = generate()
//
////    fun generateConstructor(): KtPrimaryConstructor {
////        return const
////    }
//}
//
//open class DataClass internal constructor() : Klass() {
//
//    override fun generateConstructor(typeParam: String): Constructor {
//        constructor = Constructor(typeParam.substringAfter('<').substringBefore('>'))
//        val modifiers = listOf("val", "var")
//        (constructor.value as? KtPrimaryConstructor)?.let { con ->
//            con.valueParameters.forEach { par ->
//                if (!par.hasValOrVar()) {
//                    par.replaceThis(factory.createParameter("${modifiers.random()} ${par.text}"))
//                }
//            }
//        }
//        return constructor
//    }
//
//    lateinit var constructor: Constructor
//    override val possibleModifiers: List<String> = listOf()
//    override val modifier: String = "data class"
//}
//
//class EnumClass internal constructor() : DataClass() {
//
//    override fun generateBody(): String {
//        val res = StringBuilder()
//        val params = constructor.value.valueParameters
//        val numOfParams = 3
//        repeat(numOfParams) {
//            val constr = params.map { it.typeReference!! }.map { generateDefValuesAsString(it.text) }
//            res.append("${Random().getRandomVariableName(3).capitalize()}(${constr.joinToString(",")}),")
//        }
//        res.deleteCharAt(res.length - 1)
//        return res.toString()
//    }
//
//    override val possibleModifiers: List<String> = listOf()
//    override val modifier: String = "enum class"
//}
//
//class Interface internal constructor(): Klass() {
//    override fun generateConstructor(typeParam: String): Constructor? = null
//    override fun generateBody(): String {
//        val res = StringBuilder()
//        repeat(5) {res.append("${Property("", false)}\n")}
//        return res.toString()
//    }
//
//    override val modifier = "interface"
//}
////
////class AnnotationClass: Class() {
////    override fun generate(): PsiElement {
////        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
////    }
////}
////
////class SealedClass: Class() {
////    override fun generate(): PsiElement {
////        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
////    }
////
////}
////
////
////class InnerClass: Class() {
////    override fun generate(): PsiElement {
////        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
////    }
//
////}
//
//
