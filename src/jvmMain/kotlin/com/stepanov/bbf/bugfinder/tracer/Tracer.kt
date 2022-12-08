package com.stepanov.bbf.bugfinder.tracer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import org.jetbrains.kotlin.psi.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.builtins.isFunctionTypeOrSubtype
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getAllPSIDFSChildrenOfType
import com.stepanov.bbf.bugfinder.util.isBlockExpr
import com.stepanov.bbf.bugfinder.util.replaceThis
import java.lang.StringBuilder

//TODO rewrite
//replace on bytecode instumentation?
class Tracer(val compiler: CommonCompiler, val project: Project) : KtVisitorVoid() {

    fun trace() {
        CompilerArgs.isMiscompilationMode = false
        project.files.forEach {
            if (it.getLanguage() == LANGUAGE.KOTLIN) {
                checker = MutationChecker(compiler, project, it)
                tree = it.psiFile as KtFile
                ctx = PSICreator.analyze(tree) ?: return
                it.changePsiFile(traceCurFile())
            }
        }
        CompilerArgs.isMiscompilationMode = true
    }

    private fun traceCurFile(): KtFile {
        //Handle all functions
        tree.getAllPSIChildrenOfType<KtNamedFunction>().forEach { it.accept(this) }
        //Classes
        tree.getAllPSIChildrenOfType<KtClass>().forEach { handleClass(it) }

        //Global vars

        //newTree.debugPrint()
        //Handle all control instructions
        tree.getAllPSIChildrenOfType<KtIfExpression>().forEach { it.accept(this) }
        tree.getAllPSIChildrenOfType<KtDoWhileExpression>().forEach { it.accept(this) }
        tree.getAllPSIChildrenOfType<KtWhileExpression>().forEach { it.accept(this) }
        tree.getAllPSIChildrenOfType<KtWhenExpression>().forEach { it.accept(this) }
        tree.getAllPSIChildrenOfType<KtTryExpression>().forEach { it.accept(this) }
        //Saving
//        tree.name = tree.name.dropLastWhile { it != '/' } + "traced/traced_" + tree.name.split('/').last()
        saveTracedFile(tree)
        return PSICreator.getPSIForFile(tree.name)
    }

    private fun handleClass(klass: KtClass) {
        if (klass.isInterface() || klass.isEnum() || klass is KtEnumEntry || klass.isAnnotation())
            return
        if (klass.getAllPSIChildrenOfType<KtFunction>().any { it.name == "toString" })
            return
        //Filter extension fields
        val fields = getFieldsFromClass(klass).filterNot { it.isExtensionDeclaration() }
        if (fields.isEmpty()) return
        val newFun = StringBuilder()
        newFun.append("var res = \"\"\n")
        for (f in fields) {
            val type = f.getType() ?: continue
            if (type.isFunctionTypeOrSubtype)
                continue
            if (f.text.contains("lateinit")) {
                newFun.append("if (this::${f.name}.isInitialized) {\n")
            }
            var c = 0
            var args = type.arguments
            if (type.isIterable()) {
                newFun.append("res += ${f.name}.map {")
                while (args.size == 1 && args.first().type.isIterable()) {
                    newFun.append("it.map {")
                    args = args.first().type.arguments
                    c++
                }
                newFun.append("it.toString() }")
                repeat(c) { newFun.append("}") }
                newFun.append("\n")
            } else {
                newFun.append("res += ${f.name}.toString()\n")
            }
            if (f.text.contains("lateinit")) {
                newFun.append("}\n")
            }
        }
        newFun.append("return res\n}\n")
        val newFunc = factory.createFunction("override fun toString(): String")
        val block = factory.createBlock(newFun.toString())
        newFunc.node.addChild(block.node, null)
        if (klass.body != null)
            checker.addNodeIfPossible(klass.body!!.rBrace!!, newFunc, true)
        else
            checker.addNodeIfPossible(klass, factory.createBlock(newFunc.text))
//            klass.body!!.addBefore(newFunc, klass.body!!.rBrace)
//        else
//            klass.add(factory.createBlock(newFunc.text))
    }

    private fun saveTracedFile(newFile: PsiFile) {
//        val dir = newFile.name.substring(0, newFile.name.indexOfLast { it == '/' })
//        File(dir).mkdir()
        BufferedWriter(FileWriter(File(newFile.name))).apply { write(newFile.text); close() }
    }

    private fun createNewBlockExpr(expr: KtExpression, msg: String): KtExpression {
        if (expr is KtBlockExpression) {
            expr.lBrace?.let { expr.deleteChildInternal(it.node) }
            expr.rBrace?.let { expr.deleteChildInternal(it.node) }
        }
        //If msg contains quotes
        val newMsg = msg.filter { it != '"' }
        return factory.createBlock("println(\"\"\"$newMsg\"\"\");\n${expr.text}")
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        val block = function.getChildOfType<KtBlockExpression>() ?: return
        //Init by args
        val trackingVars = function.valueParameters
            .asSequence()
            .filter { it.getType()?.isFunctionType == false && it.getType()?.isAnyOrNullableAny() == false }
            .map { it.name }
            .filterNotNull()
            .map { factory.createExpression("println($it);") }
            .toMutableList()
        handleBlock(block, trackingVars)
    }

    private fun handleBlock(block: PsiElement, trackingVars: List<KtExpression>) {
        val curTrack = trackingVars.toMutableList()
        for (el in block.getAllPSIDFSChildrenOfType<PsiElement>()) {
            if (el.getParentOfType<KtBlockExpression>(true) != block || el.getParentOfType<KtClassBody>(true) != block)
                continue
            if (el is KtClassBody && el.isBlockExpr()) {
                if (el.text != block.text)
                    handleBlock(el, curTrack)
            }
            if (el is KtBlockExpression) {
                if (el.text != block.text)
                    handleBlock(el, curTrack)
            }
            if (el is KtProperty) {
                val clazzWithPropType = getClassWithName(el.getType()?.toString())
                if (clazzWithPropType != null &&
                    clazzWithPropType.getAllPSIChildrenOfType<KtNamedFunction>().all { it.name != "toString()" }
                ) continue
                if (!el.hasInitializer()) continue
                // Handle objectLiteral
                if (el.initializer is KtObjectLiteralExpression) continue
                if (el.getType()?.isFunctionTypeOrSubtype == true || el.getType()
                        ?.isAnyOrNullableAny() == true
                ) continue
                if (el.name != null) {
                    curTrack.add(factory.createExpression("println(${el.name});"))
                }
            }
            if (el is KtReturnExpression) {
                val ret = el
                if (ret.parent is KtBlockExpression) {
                    curTrack.forEach {
                        ret.parent.addBefore(it, ret)
                        ret.parent.addBefore(factory.createWhiteSpace("\n"), ret)
                    }
                } else {
                    val newBlock = factory.createBlock("${curTrack.joinToString("\n") { it.text }}\n${ret.text}")
                    ret.replaceThis(newBlock)
                }
            }
        }
    }

    override fun visitIfExpression(expression: KtIfExpression) {
        expression.then?.let { it.replaceThis(createNewBlockExpr(it, "THEN")) }
        expression.`else`?.let { it.replaceThis(createNewBlockExpr(it, "ELSE")) }
    }

    override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
        val msg = "DO WHILE (\${${expression.condition?.text}})"
        expression.body?.let { it.replaceThis(createNewBlockExpr(it, msg)) }
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        val msg = "WHILE (\${${expression.condition?.text}})"
        expression.body?.let { it.replaceThis(createNewBlockExpr(it, msg)) }
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        for (entire in expression.entries) {
            entire.expression?.let {
                it.replaceThis(
                    createNewBlockExpr(
                        it, "WHEN ${
                            entire.conditions.joinToString {
                                if (it.text.first() == '"')
                                    it.text.substring(1, it.textLength - 1)
                                else
                                    it.text
                            }
                        }"
                    )
                )
            }
        }
    }

    override fun visitTryExpression(expression: KtTryExpression) {
        expression.tryBlock.replaceThis(createNewBlockExpr(expression.tryBlock, "TRY"))
        for (catch in expression.catchClauses) {
            catch.catchBody?.let { it.replaceThis(createNewBlockExpr(it, "CATCH ${catch.catchParameter?.text}")) }
        }
        expression.finallyBlock?.finalExpression?.let { it.replaceThis(createNewBlockExpr(it, "FINALLY")) }
    }

    private fun getFieldsFromClass(klass: KtClass): List<KtExpression> {
        val res = mutableListOf<KtExpression>()
        klass.primaryConstructorParameters.forEach {
            if (it.hasValOrVar()) res.add(it)
        }
        klass.getProperties().forEach { res.add(it) }
        return res
    }


    private fun getClassWithName(name: String?): KtClass? =
        tree.getAllPSIChildrenOfType<KtClass>().find { it.name == name }

    private fun KotlinType.isIterable(): Boolean =
        this.memberScope.getFunctionNames().any { it.toString() == "iterator" }

    private fun KtExpression.getType(): KotlinType? {
        val typesOfExpressions = this.getAllPSIChildrenOfType<KtExpression>().map { ctx.getType(it) }.filterNotNull()
        val typeReferences =
            this.getAllPSIChildrenOfType<KtTypeReference>().map { it.getAbbreviatedTypeOrType(ctx) }.filterNotNull()
        return when {
            typesOfExpressions.isNotEmpty() -> typesOfExpressions.first()
            typeReferences.isNotEmpty() -> typeReferences.first()
            else -> null
        }
    }

    private val factory = Factory.psiFactory
    private lateinit var checker: MutationChecker
    private lateinit var tree: KtFile
    private lateinit var ctx: BindingContext

}