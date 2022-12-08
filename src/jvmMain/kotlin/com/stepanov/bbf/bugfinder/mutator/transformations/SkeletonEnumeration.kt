package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.util.getAllChildrenOfCurLevel
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfTwoTypes
import com.stepanov.bbf.bugfinder.util.getAllPSIDFSChildrenOfType
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.debugPrint
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.util.replaceThis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import java.util.*
import kotlin.random.Random
import kotlin.system.exitProcess
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory


typealias UsageInfo = Triple<KotlinType, Int, MutableList<KtNameReferenceExpression>>

class SkeletonEnumeration : Transformation() {
    override fun transform() {
        var prevScope = 0
        val ctx = PSICreator.analyze(file) ?: return
        for (el in file.getAllPSIDFSChildrenOfType<PsiElement>()) {
            val scope = getDepth(el)
            if (prevScope != scope) {
                if (prevScope > scope) {
                    removeScope(prevScope)
                }
                prevScope = scope
            }
            if (el is KtProperty && el.nameIdentifier != null) {
                val type = el.calcType(ctx) ?: continue
                programInfo[el.nameIdentifier!!] = UsageInfo(type, getDepth(el), mutableListOf())
            }
            if (el is KtParameter && el.nameIdentifier != null) {
                val type = el.getType(ctx) ?: continue
                programInfo[el.nameIdentifier!!] = UsageInfo(type, getDepth(el), mutableListOf())
            }
            if (el is KtNameReferenceExpression /*&& el.parent !is KtCallExpression*/) {
                addUsage(el.text, el)
            }
        }
        repeat(maxOf(programInfo.entries.sumBy { it.value.third.size } * 50, 150)) {
            shuffle()
        }
    }

    private fun shuffle(): Boolean {
        val (_, value1) = programInfo.entries.randomOrNull() ?: return false
        val randomValue = value1.third.randomOrNull() ?: return false
        val rvDepth = getDepth(randomValue)
//        if (!checkBinExp(key1, randomValue)) return false
        val avProps = getSlice(randomValue)
            .filter { it is KtProperty || it is KtParameter }
            .map { it as KtNamedDeclaration }
            .mapNotNull { it.nameIdentifier }
            .filter { getDepth(it) <= rvDepth }
        val replacement = programInfo.entries.filter { it.key in avProps }
            //.filter { it.value.first == value1.first }
            .randomOrNull() ?: return false
        val replacementNode = replacement.value.third.randomOrNull() ?: return false
        if (!checkBinExp(replacement.key, randomValue)) return false
        //Replacing
        val backUp = randomValue.copy() as KtNameReferenceExpression
        val newNode = psiFactory.createExpression(replacementNode.text) as KtNameReferenceExpression
        value1.third.replaceAll { if (it == randomValue) newNode else it }
        randomValue.replaceThis(newNode)
        if (!checker.checkCompiling()) {
            value1.third.replaceAll { if (it == newNode) backUp else it }
            newNode.replaceThis(backUp)
            if (!checker.checkCompiling()) {
                exitProcess(1)
            }
        }
        return true
    }

    private fun checkBinExp(key: PsiElement, randomValue: PsiElement): Boolean {
        if (randomValue.parent is KtBinaryExpression) {
            val binExp = randomValue.parent as KtBinaryExpression
            if (binExp.operationToken == KtTokens.EQ && randomValue == binExp.left) {
                val prop = key.parent as? KtProperty ?: return false
                if (!prop.isVar) return false
            }
        }
        return true
    }

    internal fun getSlice(node: PsiElement): Set<KtExpression> {
        val res = mutableSetOf<KtExpression>()
        node.getAllParentsWithoutThis().zipWithNext().forEach {
            for (prop in getPropsUntil(it.second, it.first)) res.add(prop)
        }
        return res
    }

    private fun removeScope(curScope: Int) =
        programInfo.filter { it.value.second == curScope }.forEach {
            val el = programInfo[it.key]!!
            programInfo[it.key] = Triple(el.first, Int.MIN_VALUE, el.third)
        }

    private fun addUsage(name: String, usage: KtNameReferenceExpression) =
        programInfo.filterKeys { it.text == name }.values.maxByOrNull { it.second }?.third?.add(usage) ?: false


    private fun KtProperty.calcType(ctx: BindingContext): KotlinType? {
        typeReference?.getAbbreviatedTypeOrType(ctx)?.let { return it }
        initializer?.getType(ctx)?.let { return it }
        return null
    }

    private fun getDepth(el: PsiElement) = el.parents.count { it is KtBlockExpression || it is KtClassBody }

    private val programInfo: MutableMap<PsiElement, UsageInfo> = mutableMapOf()

}