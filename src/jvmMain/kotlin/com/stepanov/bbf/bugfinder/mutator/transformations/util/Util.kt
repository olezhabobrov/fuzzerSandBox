package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.stepanov.bbf.bugfinder.mutator.transformations.util.FileMember
import com.stepanov.bbf.bugfinder.util.getAllPSIDFSChildrenOfType
import com.stepanov.bbf.reduktor.util.getAllChildren
import com.stepanov.bbf.bugfinder.util.flatMap
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

internal fun getSlice(node: PsiElement): Set<KtExpression> {
    val res = mutableSetOf<KtExpression>()
    for (prop in getPropsUntil(node.parent, node)) {
        res.addAll(prop.getAllPSIDFSChildrenOfType())
    }
    //getPropsUntil(node.parent, node).forEach { res.addAll(it.getAllPSIDFSChildrenOfType()) }
    node.getAllParentsWithoutThis().zipWithNext().forEach {
        for (prop in getPropsUntil(it.second, it.first)) res.add(prop)
        //getPropsUntil(it.second, it.first).forEach { res.add(it) }
    }
    return res
}

internal fun PsiElement.getAllParentsWithoutThis(): List<PsiElement> {
    val result = arrayListOf<ASTNode>()
    var node = this.node.treeParent ?: return arrayListOf<PsiElement>()
    while (true) {
        result.add(node)
        if (node.treeParent == null)
            break
        node = node.treeParent
    }
    return result.map { it.psi }
}

fun getPropsUntil(node: PsiElement, until: PsiElement) =
    node.getAllChildren()
        .takeWhile { it != until }
        .filter { it !is KtNamedFunction && it !is KtClassOrObject && it is KtExpression }
        .flatMap { it.getAllPSIDFSChildrenOfType<KtExpression>() }

internal fun <T> List<T>.filterDuplicates(comparator: Comparator<T>): List<T> {
    val res = mutableListOf<T>()
    this.forEach { el -> if (res.all { comparator.compare(it, el) != 0 }) res.add(el) }
    return res
}


fun PsiElement.getPath() = this.parents
    .filter { it is KtNamedFunction || it is KtClassOrObject }
    .map { it as PsiNamedElement }
    .toList().reversed()

//DeclarationDescriptorsUtils
fun DeclarationDescriptor.getParents(): List<DeclarationDescriptor> =
    this.parents
        .filter {
            it !is ModuleDescriptor &&
                    it !is PackageFragmentDescriptor &&
                    it !is PackageViewDescriptor
        }
        .toList()

fun DeclarationDescriptor.getReturnTypeForCallableAndClasses() =
    when (this) {
        is CallableDescriptor -> this.returnType
        is ClassDescriptor -> this.defaultType
        else -> null
    }