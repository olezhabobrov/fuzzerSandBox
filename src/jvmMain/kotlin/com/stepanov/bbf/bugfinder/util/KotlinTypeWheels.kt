package com.stepanov.bbf.bugfinder.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isAnyOrNullableAny
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

fun List<TypeParameterDescriptor>.sortedByTypeParams(): List<TypeParameterDescriptor> {
    return this.sortedWith(typeParamDescComparator)
}


private val typeParamDescComparator = Comparator { t1: TypeParameterDescriptor, t2: TypeParameterDescriptor ->
    if (t1.upperBounds.isEmpty() && t2.upperBounds.isEmpty()) {
        0
    } else {
        val t1Args = t1.upperBounds.flatMap { listOf("$it") + it.getAllTypeParams().map { "${it.type}" } }
        if (t1Args.any { it == "${t2.defaultType}" }) 1 else -1
    }
}

fun DeclarationDescriptor.findPsi(): PsiElement? {
    val psi = (this as? DeclarationDescriptorWithSource)?.source?.getPsi()
    return if (psi == null && this is CallableMemberDescriptor && kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
        overriddenDescriptors.mapNotNull { it.findPsi() }.firstOrNull()
    } else {
        psi
    }
}

fun SourceElement.getPsi(): PsiElement? = (this as? PsiSourceElement)?.psi

fun KotlinType.replaceTypeArgsToTypes(map: Map<String, String>): String {
    val realType =
        when {
            isTypeParameter() -> map[this.constructor.toString()] ?: map["${this.constructor}?"] ?: this.toString()
            this.isNullable() -> "${this.constructor}?"
            else -> this.constructor.toString()
        }
    val typeParams = this.arguments.map { it.type.replaceTypeArgsToTypes(map) }
    return if (typeParams.isNotEmpty()) "$realType<${typeParams.joinToString()}>" else realType
}

val KotlinType.name: String?
    get() = this.constructor.declarationDescriptor?.name?.asString()

fun ClassDescriptor.isFunInterface(): Boolean {
    if (this.kind != ClassKind.INTERFACE) return false
    if (!this.isFun) return false
    return this.unsubstitutedMemberScope.getDescriptorsFiltered { true }.size == 4
}

fun KotlinType.isIterable() =
    this.memberScope.getDescriptorsFiltered { true }.any {
        it.toString().contains("operator fun iterator")
    }

fun KotlinType.isUserType(project: Project, module: ModuleDescriptor): Boolean {
    val userClasses = StdLibraryGenerator.getUserClassesDescriptorsFromProject(project, module)
    return userClasses.any { it.name == this.constructor.declarationDescriptor?.name }
}

fun getAllClassesFromPackage(pack: PackageViewDescriptor) =
    pack.memberScope
        .getDescriptorsFiltered { true }
        .filterIsInstance<ClassDescriptor>()
        .flatMap { getAllClassesFromPackage1(it) }

fun getAllClassesFromPackage(pack: PackageFragmentDescriptor) =
    pack.getMemberScope()
        .getDescriptorsFiltered { true }
        .filterIsInstance<ClassDescriptor>()
        .flatMap { getAllClassesFromPackage1(it) }


fun getAllClassesFromPackageWhichContainsType(type: KotlinType): List<ClassDescriptor> {
    if (type.constructor.declarationDescriptor?.containingDeclaration == null) return emptyList()
    val pack = type.constructor.declarationDescriptor?.findPackage() ?: return emptyList()
    return getAllClassesFromPackage(pack)
}

fun ClassifierDescriptor.getAllSuperClassifiersWithoutAnyAndItself(): Sequence<ClassifierDescriptor> =
    this.getAllSuperClassifiers().filter { it.name != this.name && !it.defaultType.isAnyOrNullableAny() }

private fun getAllClassesFromPackage1(classDescriptor: ClassDescriptor): List<ClassDescriptor> {
    val nestedClasses =
        classDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }.filterIsInstance<ClassDescriptor>()
    return if (nestedClasses.isEmpty()) listOf(classDescriptor)
    else listOf(classDescriptor) + nestedClasses.flatMap { getAllClassesFromPackage1(it) }
}

//fun TypeParameterDescriptor.getAllTypeArgs(): List<KotlinType> {
//    val res = mutableListOf<KotlinType>()
//    res.add(this.defaultType)
//    this.upperBounds.forEach
//}