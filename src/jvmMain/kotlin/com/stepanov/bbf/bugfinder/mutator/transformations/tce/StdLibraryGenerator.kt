package com.stepanov.bbf.bugfinder.mutator.transformations.tce

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackageStatement
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.EnumEntrySyntheticClassDescriptor
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.descriptorUtil.*
import org.jetbrains.kotlin.resolve.scopes.computeAllNames
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedTypeAliasDescriptor
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.*
import kotlin.random.Random

object StdLibraryGenerator {

    val descriptorDecl: List<DeclarationDescriptor>
    val klasses: List<ClassDescriptor>
    val funDescriptors: List<SimpleFunctionDescriptor>
    private val maxDepth = 2
    private val blockList = listOf("hashCode", "toString")
    private val userClasses: MutableList<ClassDescriptor> = mutableListOf()

    //TODO
    init {
        val psi = PSICreator.getPSIForText("val a: StringBuilder = StringBuilder(\"\")")
        val ctx = PSICreator.analyze(psi)!!
        val kType =
            psi.getAllPSIChildrenOfType<KtProperty>().map { it.typeReference?.getAbbreviatedTypeOrType(ctx) }[0]!!
        val module = kType.constructor.declarationDescriptor!!.module
        val stringPackages =
            if (CompilerArgs.getCompilersList().any { it.compilerInfo.contains("JS") }) {
                module.getSubPackagesOf(FqName("kotlin")) { true } +
                        listOf(FqName("kotlin"))
            } else {
                module.getSubPackagesOf(FqName("kotlin")) { true } +
                        listOf(
                            FqName("kotlin"),
                            FqName("java.util"),
                            FqName("java.math"),
                            FqName("java.nio.file"),
                            FqName("java.nio")
                        )
            }

        val packages = stringPackages
            .map { module.getPackage(it) }
            .filter { it.name.asString().let { it != "browser" && it != "js" } }
        descriptorDecl = packages.flatMap { it.memberScope.getDescriptorsFiltered { true } }
        klasses = descriptorDecl
            .mapNotNull {
                when (it) {
                    is ClassDescriptor -> it
                    is DeserializedTypeAliasDescriptor -> it.classDescriptor
                    else -> null
                }
            }
        val blockListOfFuncNames =
            listOf("toString", "hashCode", "equals", "valueOf", "values", "of", "copyOf", "enumValues")
        val funDescriptorsInsideClasses =
            klasses.flatMap { it.staticScope.getDescriptorsFiltered { true } }
                .flatMap {
                    if (it is SimpleFunctionDescriptor && it.visibility.isPublicAPI) {
                        listOf(it)
                    } else if (it is ClassDescriptor) {
                        it.unsubstitutedMemberScope.getDescriptorsFiltered { true }
                            .filterIsInstance<SimpleFunctionDescriptor>()
                    } else listOf(null)
                }.filterNotNull()

        val allFunDescriptors =
            (descriptorDecl.filterIsInstance<SimpleFunctionDescriptor>() + funDescriptorsInsideClasses)
        funDescriptors = allFunDescriptors.filter { it.name.asString() !in blockListOfFuncNames }
    }

    fun generateCallSequenceToGetType(fromType: KotlinType, toType: KotlinType): List<List<CallableDescriptor>> {
        val resForType = gen(fromType, toType)
        val resForSuperTypes = fromType.supertypes().getAllWithoutLast().flatMap { gen(it, toType) }.toList()
        val res = resForType + resForSuperTypes
        return res.filterDuplicatesBy { it.joinToString { it.name.asString() } }
    }

    fun findPackagesForTypes(types: List<String>): List<PackageFragmentDescriptor> =
        types.mapNotNull { findPackageForType(it) }

    fun findPackageForType(type: String): PackageFragmentDescriptor? {
        val klassDescriptor = klasses.find { it.name.asString() == type.substringBefore('<').trim() }
        return klassDescriptor?.containingDeclaration?.findPackage()
    }

    fun generateOpenClassType(onlyInterfaces: Boolean): ClassDescriptor {
        val openKlasses =
            klasses
                .filter { it.visibility.isPublicAPI && it.modality != Modality.FINAL }
                .filterNot { it.containingDeclaration.findPackage().toString().contains("js") }
                .filter { it.constructors.isEmpty() || it.constructors.any { it.visibility.isPublicAPI } }
                .filter { it.constructors.isEmpty() || !onlyInterfaces }
        return openKlasses.random()
    }

    private fun checkVisibility(decl: DeclarationDescr): Boolean =
        checkVisibility(decl.descriptor)


    private fun checkVisibility(decl: DeclarationDescriptor): Boolean {
        return when (decl) {
            is SimpleFunctionDescriptor -> decl.visibility.isPublicAPI
            is PropertyDescriptor -> decl.visibility.isPublicAPI
            else -> false
        }
    }

    private fun checkVisibilityAndModality(decl: DeclarationDescriptor): Boolean {
        if (!checkVisibility(decl)) return false
        return when (decl) {
            is SimpleFunctionDescriptor -> decl.modality != Modality.FINAL
            is PropertyDescriptor -> decl.modality != Modality.FINAL
            else -> false
        }
    }

    fun gen(
        type: KotlinType,
        needType: KotlinType,
        prefix: List<CallableDescriptor> = listOf()
    ): List<List<CallableDescriptor>> {
//        println("TYPE = $type")
//        println("NEEDTYPE = $needType")
        if (prefix.size == maxDepth) return listOf()
        val extFuncsFromLib =
            getExtensionFuncsFromStdLibrary(type, needType)
                .filter { checkVisibility(it) }
                .filter { decl ->
                    if (prefix.size == maxDepth - 1) {
                        decl.retValueType?.isTypeParameter() == true || decl.retValueType?.name == needType.name
                    } else true
                }
                .shuffled().take(3)
        val funList: MutableList<List<CallableDescriptor>> = mutableListOf()
        generateForRandomLibraryFuncs(type, needType, extFuncsFromLib, funList, prefix)
        val derivedTypes = mutableListOf<Pair<List<CallableDescriptor>, KotlinType>>()
        for (mem in getMemberFields(type).shuffled().take(2)) {
            val descriptor =
                mem as? CallableMemberDescriptor ?: mem as? CallableMemberDescriptor ?: continue
            if (descriptor.name.asString() in blockList) continue
            if (!descriptor.visibility.isPublicAPI) continue
            if (descriptor.returnType?.toString()
                    .let { it == "$needType" || "$it?" == "$needType" || it == "$needType?" }
            ) {
                funList.add(prefix + descriptor)
            }
            val anotherType = descriptor.returnType ?: continue
            derivedTypes.add(prefix + descriptor to anotherType)
        }
        derivedTypes
            .filter { it.second.toString() != type.toString() }
            .filterDuplicatesBy { it.second.toString() }
            .flatMap { gen(it.second, needType, it.first) }
            .forEach { funList.add(prefix + it) }
        return funList
    }

    private fun generateForRandomLibraryFuncs(
        type: KotlinType,
        needType: KotlinType,
        extWithTypeParams: List<DeclarationDescr>,
        funList: MutableList<List<CallableDescriptor>>,
        prefix: List<CallableDescriptor> = listOf()
    ) {
        val needTypeName = needType.getNameWithoutError()//.constructor.declarationDescriptor?.name
        extWithTypeParams.forEach { decl ->
            //for (decl in extWithTypeParams) {
            if (decl.recType?.name == null) return@forEach
            val newTypeParameters = mutableMapOf<String, KotlinType>()
            if (decl.recType.name in listOf(type.name) + type.supertypesWithoutAny().map { it.name ?: "" }) {
                if (type.arguments.size == decl.recType.arguments.size) {
                    val replacedTypeArgs = decl.recType.arguments.withIndex().associateBy({
                        it.value.type.constructor.declarationDescriptor?.name?.asString() ?: "${it.value}"
                    }) {
                        type.arguments[it.index].type
                    }
                    newTypeParameters.putAll(replacedTypeArgs)
                }
            }
            decl.descriptor.typeParameters.forEachIndexed { i, it ->
                if (newTypeParameters.containsKey(it.name.asString())) return@forEachIndexed
                var bound = it.upperBounds.firstOrNull()
                if (bound != null && bound.getAllTypeParamsWithItself().isNotEmpty()) {
                    val substitutedArgs = bound.arguments.map { arg ->
                        arg.substitute {
                            newTypeParameters[it.getNameWithoutError()] ?: arg.type
                        }
                    }
                    bound = bound.replace(substitutedArgs)
                }
                val rtvName = decl.retValueType?.constructor?.declarationDescriptor?.name
                val isNeedTypeSatisfyingToBounds = bound == null || bound.isNullableAny() ||
                        needType.supertypesWithoutAny().any {
                            it.constructor.declarationDescriptor?.name == bound.constructor.declarationDescriptor?.name
                        }
                val extReceiverTP = decl.recType.getAllTypeParamsWithItself().firstOrNull()
                val prob = if (prefix.size == maxDepth - 1) 100 else 75
                val newType = when {
                    extReceiverTP?.type?.constructor?.declarationDescriptor?.name == it.name -> type
                    rtvName == it.name && prefix.size == maxDepth - 1 -> null
                    Random.getTrue(prob) && isNeedTypeSatisfyingToBounds -> needType
                    prob == 100 -> null
                    else -> RandomTypeGenerator.generateRandomTypeWithCtx(bound)
                } ?: return@forEach
                val newArgs = newType
                    .getAllTypeParamsWithItself()
                    .map { it to it.type.constructor.declarationDescriptor?.name?.asString() }
                    .filterDuplicatesBy { it.second ?: "" }
                    .map { t -> newTypeParameters[t.second]?.asTypeProjection() ?: t.first.type.asTypeProjection() }
                newTypeParameters[it.name.asString()] = newType.replace(newArgs)
            }
            val typeSubstitutor = TypeSubstitutor.create(
                decl.descriptor.typeParameters
                    .withIndex()
                    .associateBy({ it.value.typeConstructor }) {
                        TypeProjectionImpl(newTypeParameters[it.value.name.asString()]!!)
                    }
            )
            val newDescriptor = decl.descriptor.substitute(typeSubstitutor)
            //println("NEWDESC = $newDescriptor")
            val funRetType = newDescriptor.returnType ?: return@forEach
            try {
                if (funRetType.getNameWithoutError() == needTypeName) {
                    funList.add(prefix + newDescriptor)
                } else {
                    val generatedFunSequence = gen(funRetType, needType, prefix + newDescriptor)
                    funList.addAll(generatedFunSequence)
                }
            } catch (e: Exception) {
            }
        }
    }


    private fun List<PsiElement>.createTypeCallSeq(): String =
        this.joinToString(".") {
            when (it) {
                is KtProperty -> it.name ?: ""
                else -> (it as KtNamedFunction).name ?: ""
            }
        }


    private fun getMembersToOverride1(kl: KotlinType): List<DeclarationDescriptor> {
        kl.memberScope.computeAllNames()
        return kl.memberScope.getDescriptorsFiltered { true }.toList() +
                kl.supertypesWithoutAny().flatMap { getMembersToOverride1(it) }
    }

    fun getMembersToOverride(kl: KotlinType): List<DeclarationDescriptor> {
        val fields = getMembersToOverride1(kl)
            .filterDuplicatesBy {
                if (it is FunctionDescriptor) "${it.name}${it.valueParameters.map { it.name.asString() + it.returnType.toString() }}"
                else it.name.asString()
            }
            .filter {
                it.toString().let { !it.contains("equals") && !it.contains("hashCode") && !it.contains("toString") } &&
                        checkVisibilityAndModality(it)
            }
        return fields
            .filter { it is PropertyDescriptor || it is FunctionDescriptor }
    }

    private fun getExtensionFuncsFromStdLibraryWithTP(type: KotlinType): List<DeclarationDescr> {
        val res = mutableListOf<DeclarationDescr>()
        val filteredDescDecl = descriptorDecl
            .filter { it is SimpleFunctionDescriptor || it is PropertyDescriptor }
            .filter {
                val tp =
                    if (it is SimpleFunctionDescriptor) it.typeParameters
                    else (it as PropertyDescriptor).typeParameters
                tp.isNotEmpty()
            }
        for (desc in filteredDescDecl) {
            val rec = when (desc) {
                is SimpleFunctionDescriptor -> desc.dispatchReceiverParameter
                    ?: desc.extensionReceiverParameter
                is PropertyDescriptor -> desc.dispatchReceiverParameter ?: desc.extensionReceiverParameter
                else -> null
            }
            val retValueType = (desc as? CallableMemberDescriptor)?.returnType
            if (retValueType?.getAllTypeParamsWithItself()?.isEmpty() == true) continue
            val recValueTypeAsString = rec?.value?.type?.constructor?.declarationDescriptor?.name?.asString()
                ?: rec?.value?.type?.toString()?.substringBefore('<')
            val typeName = type.constructor.declarationDescriptor?.name?.asString() ?: "$type".substringBefore('<')
            if (recValueTypeAsString != typeName) continue
            val recType = rec?.value?.type ?: continue
            val recTypeParams = recType
                .getAllTypeParamsWithItself()
                .filterDuplicatesBy { it.type.getNameWithoutError() }
                .toSet()
            if (/*recTypeParams?.size == 1 &&*/
                (retValueType?.isTypeParameter() == true || recValueTypeAsString == typeName)
            ) {
                val callableDecr = (rec.containingDeclaration as? CallableDescriptor) ?: continue
                if (callableDecr.typeParametersCount > 3) continue
                val recTpBound = callableDecr
                    .typeParameters
                    .map { it to it.upperBounds.firstOrNull() }
                    .filter { it.first.name.asString() in recTypeParams.map { it.type.getNameWithoutError() } }
                var fl = true
                for (recTP in recTypeParams) {
                    val bound =
                        recTpBound.find { it.first.name.asString() == recTP.type.getNameWithoutError() }?.second
                            ?: continue
                    fl = !type.supertypes()
                        .all { it.constructor.declarationDescriptor?.name != bound.constructor.declarationDescriptor?.name }
                }
                if (fl) res.add(DeclarationDescr(callableDecr, rec.returnType, retValueType))
            }
        }
        return res
    }

    private fun getExtensionFuncsFromStdLibrary(type: KotlinType, needType: KotlinType): List<DeclarationDescr> {
        val res = mutableListOf<DeclarationDescr>()
        val typeName = type.constructor.declarationDescriptor?.name
//        println("TRYING TO GET SUPERTYPES FOR ${type}")
        val superTypesName = type.supertypesWithoutAny().toList().map { it.constructor.declarationDescriptor?.name }
        val typeAndSupertypesNames = listOf(typeName) + superTypesName
        for (desc in descriptorDecl) {
            val rec = when (desc) {
                is SimpleFunctionDescriptor -> desc.dispatchReceiverParameter
                    ?: desc.extensionReceiverParameter
                is PropertyDescriptor -> desc.dispatchReceiverParameter ?: desc.extensionReceiverParameter
                else -> null
            } ?: continue
            val retValueType = (desc as? CallableMemberDescriptor)?.returnType
            val recName = rec.value.type.constructor.declarationDescriptor?.name
            if (recName in typeAndSupertypesNames) {
                if (type.arguments.size != rec.value.type.arguments.size) continue
                var fl = true
                rec.value.type.arguments.forEachIndexed { i, arg ->
                    val argName = arg.type.constructor.declarationDescriptor?.name
                    val typeArgName = type.arguments[i].type.constructor.declarationDescriptor?.name
                    if (!arg.type.isTypeParameter() && argName != typeArgName)
                        fl = false
                }
                if (!fl) continue
                (rec.containingDeclaration as? CallableDescriptor).let {
                    res.add(DeclarationDescr(it!!, rec.returnType, retValueType))
                }
            }
        }
        return res
    }

    private fun getMemberFields(type: KotlinType): List<DeclarationDescriptor> =
        type.memberScope.getDescriptorsFiltered { true }
            .filter { !it.toString().contains("private") }
            .filter { it.name.asString().let { it != "equals" && it != "hashCode" && it != "toString" } }

    private fun createFunDefinitionFromDeclarationDescriptor(
        typeParamsToArgs: Map<String, String>,
        decl: DeclarationDescriptor
    ): KtNamedFunction? {
        val funDecl =
            decl as? CallableMemberDescriptor ?: return Factory.psiFactory.createFunction("fun a()")
        val typeParam =
            if (funDecl.typeParameters.isNotEmpty()) {
                val withUpperBounds = funDecl.typeParameters.map {
                    val type = it.defaultType.replaceTypeArgsToTypes(typeParamsToArgs)
                    val upperBounds = if (it.upperBounds.size == 1 && it.upperBounds[0].isAnyOrNullableAny())
                        ""
                    else
                        it.upperBounds
                            .map { it.replaceTypeArgsToTypes(typeParamsToArgs) }
                            .filter { it.isNotEmpty() }
                            .joinToString()
                    if (upperBounds.isEmpty()) type else "$type : $upperBounds"
                }.joinToString()
                "<$withUpperBounds>"
            } else ""
        val rec = funDecl.dispatchReceiverParameter ?: funDecl.extensionReceiverParameter
        val recWithoutTypeParam = rec?.value?.type?.replaceTypeArgsToTypes(typeParamsToArgs) ?: rec.toString()
        val params = handleParams(decl.valueParameters, typeParamsToArgs)
        val func = "fun $typeParam $recWithoutTypeParam.${decl.name}($params): ${decl.returnType}{}"
        return try {
            Factory.psiFactory.createFunction(func)
        } catch (e: Exception) {
            println("cant create fun ${func}")
            System.exit(1)
            null
        }
    }

    fun getLibraryCallsForType(type: KotlinType, needType: KotlinType): List<SimpleFunctionDescriptor> {
        val superTypes =
            type.constructor.declarationDescriptor?.getAllSuperClassifiers()?.filter { it.name.asString() != "Any" }
                ?.map { it.name.toString().substringBefore('<') }
                ?.toList() ?: listOf()
        val condition2 = { a: SimpleFunctionDescriptor ->
            a.extensionReceiverParameter?.value?.type?.let {
                val rtv = "${a.returnType}".substringBefore('<')
                "$it".substringBefore('<') in superTypes && rtv in superTypes
            } ?: false
        }
        return listOf()
    }

    fun searchForFunWithRetType(type: KotlinType): List<SimpleFunctionDescriptor> {
        val implementations =
            findImplementationOf(type, false)
                .filterDuplicatesBy { it.name }
                .map { it.name.asString().substringBefore('<') }.toMutableSet()
        implementations.add(type.toString().substringBefore('<'))
        return funDescriptors.asSequence()
            .filter { it.returnType?.getNameWithoutError()?.substringBefore('<') in implementations }
            .filter { it.returnType?.let { checkTypeParams(type, it) } ?: false }
            .filter {
                it.extensionReceiverParameter?.value?.type?.let { !it.compareStringRepOfTypesWithoutTypeParams(type) }
                    ?: true
            }
            .filter { it.visibility.isPublicAPI }
            .toList()
    }

    private fun checkTypeParams(toType: KotlinType, retValueType: KotlinType): Boolean {
        if (retValueType.isTypeParameter()) return true
        if (retValueType.arguments.size != toType.arguments.size) return false
        if (retValueType.arguments.isEmpty() && toType.arguments.isEmpty()) {
            return retValueType.constructor.toString() == toType.constructor.toString()
        }
        for (i in retValueType.arguments.indices) {
            val retValueTypeParam = retValueType.arguments[i]
            val typeParam = toType.arguments[i]
            if (retValueTypeParam.type.isTypeParameter()) continue
            if (typeParam.type.constructor.toString() != retValueTypeParam.type.constructor.toString()) return false
            val argCompRes = typeParam.type.arguments.zip(retValueTypeParam.type.arguments)
                .map { checkTypeParams(it.first.type, it.second.type) }
            if (argCompRes.any { !it }) return false
        }
        return true
    }

    fun findEnumClasses(): List<ClassDescriptor> =
        klasses.filter { it.kind == ClassKind.ENUM_CLASS && it.visibility.isPublicAPI }

    fun findEnumMembers(type: KotlinType): List<KotlinType> {
        val klass = klasses
            .find {
                if (it.declaredTypeParameters.isEmpty()) {
                    it.getAllSuperClassifiers().any { it.name.asString() == type.getNameWithoutError() }
                } else {
                    it.getAllSuperClassifiers().any { it.name.asString() == type.toString().substringBefore('<') }
                }
            } ?: return listOf()
        return klass.unsubstitutedMemberScope.getContributedDescriptors { true }
            .filterIsInstance<EnumEntrySyntheticClassDescriptor>()
            .map { it.defaultType }
    }

    fun findImplementationOf(type: KotlinType, withoutInterfaces: Boolean = true): List<ClassDescriptor> =
        klasses
            .asSequence()
            .filter {
//                if (it.declaredTypeParameters.isEmpty()) {
//                    it.getAllSuperClassifiers().any { it.name.asString() == type.getNameWithoutError() }
//                } else {
//                    it.getAllSuperClassifiers().any { it.name.asString() == type.toString().substringBefore('<') }
//                }
                it.getAllSuperClassifiers().any { it.name.asString() == type.toString().substringBefore('<') }
            }
            .filter { ((it.constructors.isNotEmpty() && it.modality != Modality.ABSTRACT) || !withoutInterfaces) && it.visibility.isPublicAPI }
            .filter {
                if (it.constructors.isNotEmpty() && !it.defaultType.isPrimitiveTypeOrNullablePrimitiveTypeOrString()) {
                    it.constructors.any { it.visibility.isPublicAPI && it.visibility.name != "protected" }
                } else true
            }
            //TODO ??
//            .filter { it.name != type.constructor.declarationDescriptor?.name }
            .toList()

    fun calcImports(file: KtFile): List<String> {
        val klassesForImport =
            klasses.filter { it.classId?.packageFqName?.asString() != "kotlin" }
        val types = file.getAllPSIChildrenOfType<KtReferenceExpression>().map { it.text.trim() }.toSet()
        return types.mapNotNull { t ->
            klassesForImport.find { it.name.asString() == t.substringBefore('<') }.classId?.asString()
                ?.replace('/', '.')
        }
    }

    fun findImplementationFromFile(type: KotlinType, withoutInterfaces: Boolean = true): List<ClassDescriptor> =
        getAllClassesFromPackageWhichContainsType(type)
            .filter { it.name.asString() != type.toString().substringBefore('<') }
            .filter { it.getAllSuperClassifiers().any { it.name.asString() == type.toString().substringBefore('<') } }
            .filter { (!it.defaultType.isInterface() && !it.defaultType.isAbstractClass()) || !withoutInterfaces }

    fun isImplementation(type: KotlinType, potImpl: KotlinType?): Boolean {
        if (potImpl == null) return true
        (findImplementationFromFile(type) + findImplementationOf(type))
            .find { it.name.asString() == potImpl.constructor.toString() }
            ?.let { return true } ?: return false
    }

    fun getListOfExceptionsFromStdLibrary(): List<String> {
        return klasses
            .filter { it.getAllSuperClassifiers().toList().any { it.name.asString() == "Throwable" } }
            .map { it.name.asString() }
    }

    fun getUserClassesDescriptorsFromProject(
        project: Project,
        moduleDescriptor: ModuleDescriptor
    ): List<ClassDescriptor> {
        val userPackages = project.files.map { it.psiFile }.map {
            if (it is KtFile) it.packageFqName.asString()
            else it.getAllPSIChildrenOfType<PsiPackageStatement>().firstOrNull()?.packageName
        }.map {
            if (it == null || it.isEmpty()) FqName.ROOT else FqName(it)
        }
        val packages = userPackages.map { moduleDescriptor.getPackage(it) }
        val classesFromProject = project.files
            .map { it.psiFile }
            .flatMap {
                if (it is KtFile) it.getAllPSIChildrenOfType<KtClassOrObject>().map { it.name }
                else it.getAllPSIChildrenOfType<PsiClass>().map { it.name }
            }.filterNotNull()
        val classDescriptorsFromPackage = packages.flatMap { getAllClassesFromPackage(it) }
        classDescriptorsFromPackage
            .filter { it.name.asString() in classesFromProject }
            .forEach { userClasses.add(it) }
        return userClasses
    }

    fun getAllDescendantsFromStdLibrary(classDescriptor: ClassDescriptor): Set<ClassDescriptor> =
        klasses.filter { it.getAllSuperClassifiers().any { it.name == classDescriptor.name } }.toSet()

    fun getDeclDescriptorOf(klassName: String): DeclarationDescriptor? =
        klasses.firstOrNull { it.name.asString() == klassName }

    private fun KotlinType.compareStringRepOfTypesWithoutTypeParams(other: KotlinType) =
        this.toString().substringBefore('<') == other.toString().substringBefore("<")

    private fun handleParams(valParamDesc: List<ValueParameterDescriptor>, typeParamsToArgs: Map<String, String>) =
        valParamDesc.map { "${it.name}: ${it.type.replaceTypeArgsToTypes(typeParamsToArgs)}" }.joinToString()

}

data class DeclarationDescr(
    val descriptor: CallableDescriptor,
    val recType: KotlinType?,
    val retValueType: KotlinType?
)