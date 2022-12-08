package com.stepanov.bbf.bugfinder.mutator.transformations.util

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.getParents
import com.stepanov.bbf.bugfinder.mutator.transformations.getReturnTypeForCallableAndClasses
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType

class FileFieldsTable(private val file: KtFile, private val ctx: BindingContext) {
    val fileMembersTable = mutableListOf<FileMember>()

    init {
        file.getAllPSIChildrenOfType<KtDeclaration>()
            .map { it to it.getDeclarationDescriptorIncludingConstructors(ctx) }
            .filter { it.second != null }
            .forEach {
                val parents =
                    it.second!!.getParents()
                val desc = it.second!!
                val rt = desc.getReturnTypeForCallableAndClasses() ?: return@forEach
                val entry = FileMember(parents, it.first, rt, it.second!!)
                fileMembersTable.add(entry)
            }
    }

    fun getEntry(fm: DeclarationDescriptor) =
        fileMembersTable.find { it.descriptor == fm }

    fun getAvailableDescriptors(entry: FileMember): List<FileMember> {
        val res = fileMembersTable.filter { it.path.all { it in entry.path } }.toMutableSet()
        val additionalMembers = mutableSetOf<FileMember>()
        for (member in res) {
            if (member.descriptor is ClassDescriptor) {
                additionalMembers.addAll(addClassDescriptors(member.descriptor))
            }
        }
        return (res + additionalMembers).toList()
    }

    private fun addClassDescriptors(desc: ClassDescriptor): Set<FileMember> {
        val res = mutableSetOf<FileMember>()
        val classMemberScope = desc.unsubstitutedMemberScope.getDescriptorsFiltered { true }
        val constructorMemberScope = desc.unsubstitutedPrimaryConstructor?.valueParameters ?: listOf()
        val scope = classMemberScope + constructorMemberScope
        for (childDescriptor in scope) {
            val childDescAsFileMember = fileMembersTable.find { it.descriptor == childDescriptor } ?: continue
            res.add(childDescAsFileMember)
            if (childDescriptor is ClassDescriptor) {
                res.addAll(addClassDescriptors(childDescriptor))
            }
        }
        return res
    }

}

data class FileMember(
    val path: List<DeclarationDescriptor>,
    val value: PsiElement,
    val type: KotlinType,
    val descriptor: DeclarationDescriptor
)