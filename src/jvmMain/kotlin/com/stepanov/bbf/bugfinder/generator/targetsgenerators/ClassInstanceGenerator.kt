package com.stepanov.bbf.bugfinder.generator.targetsgenerators

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.*
import org.apache.log4j.Logger
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.isInterface
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import kotlin.random.Random

internal class ClassInstanceGenerator(file: KtFile, val ctx: BindingContext) : TypeAndValueParametersGenerator(file) {

    private val log = Logger.getLogger("mutatorLogger")
    private val MAX_DEPTH = 10

    private fun generateInstanceOfLocalClass(
        klDescriptor: ClassDescriptor,
        klAsPSI: KtClassOrObject,
        depth: Int = 0
    ): Pair<PsiElement?, KotlinType>? {
        if (klAsPSI.hasModifier(KtTokens.PRIVATE_KEYWORD)) return null
        val parentClasses = klDescriptor.parentsWithSelf.toList().filterIsInstance<ClassDescriptor>().reversed()
        val res = mutableListOf<String>()
        var curClassAsKType = parentClasses.firstOrNull()?.defaultType ?: return null
        for (i in 0 until parentClasses.size - 1) {
            if (parentClasses[i + 1].isInner) {
                val (instance, resultType) = unsafeGenerateRandomInstanceOfClass(curClassAsKType, depth)
                    ?: return null
                if (instance == null) return null
                res.add(instance.text)
                val childClassType = resultType
                    .memberScope
                    .getDescriptorsFiltered { true }
                    .filterIsInstance<ClassDescriptor>()
                    .find { it.name == parentClasses[i + 1].name } ?: break
                val newArgsAmount = childClassType.defaultType.arguments.size - resultType.arguments.size
                val newTypeArgs =
                    if (newArgsAmount <= 0) listOf()
                    else childClassType.defaultType.arguments.take(newArgsAmount) + resultType.arguments
                curClassAsKType = childClassType.defaultType.replace(newTypeArgs)
            } else {
                res.add("${parentClasses[i].name}")
                curClassAsKType = parentClasses[i + 1].defaultType
            }
        }
        val i = unsafeGenerateRandomInstanceOfClass(curClassAsKType, depth) ?: return null
        res.add(i.first?.text ?: "")
        return Factory.psiFactory.createExpressionIfPossible(res.joinToString(".")) to curClassAsKType
    }

    fun generateRandomInstanceOfUserClass(
        klOrObjType: KotlinType,
        depth: Int = 0
    ): Pair<PsiElement?, KotlinType?>? {
        log.debug("Generate instance of class $klOrObjType")
        if (depth > MAX_DEPTH) return null
        val classDescriptor = klOrObjType.constructor.declarationDescriptor as? ClassDescriptor ?: return null
        if (classDescriptor.isFunInterface()) return generateFunInterfaceInstance(classDescriptor, depth)
        if (classDescriptor.name.asString().trim().isEmpty()) return null
        if (classDescriptor.parentsWithSelf.any { it is FunctionDescriptor }) return null
        log.debug("generating klass ${classDescriptor.name} depth = $depth")
        if (classDescriptor.kind == ClassKind.OBJECT) {
            val fullName =
                classDescriptor.parentsWithSelf.toList()
                    .filterIsInstance<ClassDescriptor>()
                    .reversed()
                    .joinToString(".") { it.name.asString() }
            return Factory.psiFactory.createExpressionIfPossible(fullName) to klOrObjType
        }
        val psiClassOrObj = classDescriptor.findPsi() as? KtClassOrObject ?: return null
        if (classDescriptor.kind == ClassKind.ENUM_CLASS || classDescriptor.kind == ClassKind.ENUM_ENTRY) {
            return generateEnumInstance(psiClassOrObj) to klOrObjType
        }
        if (classDescriptor.constructors.isNotEmpty() &&
            classDescriptor.constructors.all { !it.visibility.isPublicAPI }
        ) return generateImplementation(
            klOrObjType,
            depth
        )
        //return null
        if (classDescriptor.kind == ClassKind.ANNOTATION_CLASS) return null
        if (!rtg.isInitialized()) {
            rtg.setFileAndContext(file)
        }
        if (psiClassOrObj.parents.any { it is KtClassOrObject }) {
            return generateInstanceOfLocalClass(classDescriptor, psiClassOrObj, depth)
        }
        if (psiClassOrObj is KtObjectDeclaration) {
            val expr = Factory.psiFactory.createExpression(psiClassOrObj.name!!)
            return expr to klOrObjType
        }
        if (klOrObjType.isInterface() || klOrObjType.isAbstractClass() || classDescriptor.isSealed()) return generateImplementation(
            klOrObjType,
            depth
        )
        return unsafeGenerateRandomInstanceOfClass(klOrObjType, depth)
    }

    @Deprecated("Use KotlinType")
    fun generateRandomInstanceOfUserClass(
        klOrObj: KtClassOrObject,
        depth: Int = 0
    ): Pair<PsiElement?, KotlinType?>? {
        if (depth > MAX_DEPTH) return null
        log.debug("generating klass ${klOrObj.name} depth = $depth text = ${klOrObj.text}")
        if (klOrObj.name == null
            || klOrObj.allConstructors.let { it.isNotEmpty() && it.all { it.isPrivate() } }
            || klOrObj.isAnnotation()
            || klOrObj.hasModifier(KtTokens.SEALED_KEYWORD)
        ) return null
        if (klOrObj.hasModifier(KtTokens.ENUM_KEYWORD) || klOrObj is KtEnumEntry) {
            return generateEnumInstance(klOrObj) to null
        }
        if (!rtg.isInitialized()) {
            rtg.setFileAndContext(file)
        }
        val classType =
            (klOrObj.getDeclarationDescriptorIncludingConstructors(rtg.ctx) as? ClassDescriptor)?.defaultType
                ?: rtg.generateKTypeForClass(klOrObj)
                ?: return null
        if (klOrObj.parents.any { it is KtClassOrObject }) {
            return generateInstanceOfLocalClass(
                classType.constructor.declarationDescriptor as ClassDescriptor,
                klOrObj,
                depth
            )
        }
        if (klOrObj is KtObjectDeclaration) {
            val expr = Factory.psiFactory.createExpression(klOrObj.name!!)
            return expr to classType
        }
        if (classType.isInterface() || classType.isAbstractClass()) return generateImplementation(
            classType,
            depth
        )
        return unsafeGenerateRandomInstanceOfClass(classType, depth)
    }

    private fun generateEnumInstance(klOrObj: KtClassOrObject): KtExpression? {
        val klass =
            if (klOrObj is KtEnumEntry) {
                klOrObj.parents.first { it is KtClassOrObject } as? KtClassOrObject
            } else {
                klOrObj
            } ?: klOrObj
        val randomEnum = klass.body?.enumEntries?.randomOrNull() ?: return null
        return Factory.psiFactory.createExpression("${klass.name}.${randomEnum.name}")
    }

    private fun generateImplementation(implementedType: KotlinType, depth: Int): Pair<PsiElement?, KotlinType>? {
        val implementations = StdLibraryGenerator.findImplementationFromFile(implementedType, true)
        val randomImpl = implementations.randomOrNull()?.defaultType ?: return null
        val classDescriptor = randomImpl.constructor.declarationDescriptor as? ClassDescriptor ?: return null
        val typeParamsToRealTypes = TypeParamsReplacer.throwTypeParams(implementedType, classDescriptor)?.second ?: return null
        val replacedTypeArgs = randomImpl.arguments.map { typeParamsToRealTypes[it.type.getNameWithoutError()]?.asTypeProjection() ?: it }
        val typeToImplement = randomImpl.replace(replacedTypeArgs)
        val instance =
            generateRandomInstanceOfUserClass(typeToImplement, depth + 1)?.let {
                if (it.second == null) return null
                else it.first to it.second!!
            }
        if (Random.getTrue(10)) {
            val anObjImpl = generateAnonymousObjectImplementation(implementedType, depth)
            return listOfNotNull(instance, anObjImpl).randomOrNull()
        }
        return instance
    }

    private fun generateFunInterfaceInstance(
        classDescriptor: ClassDescriptor,
        depth: Int
    ): Pair<PsiElement?, KotlinType?>? {
        val typeParams = classDescriptor.declaredTypeParameters
        val newTypeParameters = generateTypeParameters(typeParams)
        if (newTypeParameters.size != classDescriptor.declaredTypeParameters.size) return null
        val typeSubstitutor = TypeSubstitutor.create(
            classDescriptor.declaredTypeParameters
                .withIndex()
                .associateBy({ it.value.typeConstructor }) {
                    TypeProjectionImpl(newTypeParameters[it.index])
                }
        )
        val subClassDescr = classDescriptor.substitute(typeSubstitutor) as? ClassDescriptor
        val substConDescr = (subClassDescr
            ?.unsubstitutedMemberScope
            ?.getDescriptorsFiltered { true }
            ?.lastOrNull {
                it.name.asString().let { it != "toString" && it != "hashCode" && it != "equals" }
            } as? FunctionDescriptor)
            ?: return null

        val numberOfParams = substConDescr.valueParameters.size
        val substitutedValueParamsAndRTV =
            substConDescr.valueParameters.map { it.returnType } + listOf(substConDescr.returnType)
        val substitutedTypeParamsAsString =
            substitutedValueParamsAndRTV
                .joinToString(", ", "<", ">") {
                    it?.getNameWithoutError() ?: "Any"
                }
        val typeParamsForDeclaration =
            substitutedValueParamsAndRTV
                .dropLast(substitutedValueParamsAndRTV.size - typeParams.size)
                .let {
                    if (it.isEmpty()) {
                        ""
                    } else {
                        it.joinToString(", ", "<", ">") {
                            it?.getNameWithoutError() ?: "Any"
                        }
                    }
                }

        val type = "Function$numberOfParams$substitutedTypeParamsAsString"
        val typeAsKotlinType = rtg.generateType(type) ?: return null
        val generatedConstructor = RandomInstancesGenerator(file, ctx).generateValueOfType(typeAsKotlinType, depth + 1)
        val instance = "${classDescriptor.name}${typeParamsForDeclaration}$generatedConstructor"
        val psiInstance = Factory.psiFactory.createExpressionIfPossible(instance) ?: return null
        return psiInstance to subClassDescr.defaultType.replace(newTypeParameters.map { it.asTypeProjection() })
    }

    //TODO fix for interfaces with type parameters
    private fun generateAnonymousObjectImplementation(
        interfaceType: KotlinType,
        depth: Int
    ): Pair<PsiElement?, KotlinType>? {
        val membersToOverride =
            StdLibraryGenerator.getMembersToOverride(interfaceType)
                .filterDuplicatesBy {
                    if (it is FunctionDescriptor) "${it.name}${it.valueParameters.map { it.name.asString() + it.returnType.toString() }}"
                    else (it as PropertyDescriptor).name.asString()
                }
        val res = StringBuilder()
        val name = interfaceType.constructor.declarationDescriptor?.name ?: return null
        res.appendLine("object: $name {")
        for (member in membersToOverride) {
            val memberToString = "$member".substringBefore(':').erase("abstract ")
            if (member is PropertyDescriptor) {
                val rtv = member.type
                val initialValue =
                    RandomInstancesGenerator(file, ctx).generateValueOfType(rtv, depth + 1)
                        .let { if (it.isEmpty()) "TODO" else it }
                res.appendLine("override $memberToString: $rtv = $initialValue")
            } else if (member is FunctionDescriptor) {
                val psi = member.findPsi() as? KtNamedFunction
                if (psi != null && psi.hasBody() && Random.getTrue(85)) continue
                val rtv = member.returnType ?: continue
                val initialValue =
                    RandomInstancesGenerator(file, ctx).generateValueOfType(rtv, depth + 1)
                        .let { if (it.isEmpty()) "TODO" else it }
                res.appendLine("override $memberToString: $rtv = $initialValue")
            }
        }
        res.appendLine("}")
        return try {
            val expr = Factory.psiFactory.createObject(res.toString())
            expr to interfaceType
        } catch (e: Exception) {
            null
        }
    }

    private fun unsafeGenerateRandomInstanceOfClass(classType: KotlinType, depth: Int): Pair<PsiElement?, KotlinType>? {
        val classDescriptor =
            classType.constructor.declarationDescriptor as? ClassDescriptor ?: return null
        val constructor =
            classDescriptor.constructors.filter { it.visibility.isPublicAPI }.randomOrNull() ?: return null
        var newTypeParameters =
            if (classType.arguments.all { !it.type.isTypeParameter() }) {
                classType.arguments.map { it.type }
            } else {
                generateTypeParameters(constructor.typeParameters)
            }
        newTypeParameters = newTypeParameters
            .zip(classType.arguments.map { it.type })
            .map { if (it.second.isTypeParameter()) it.first else it.second }

        if (newTypeParameters.size != constructor.typeParameters.size) return null
        val typeSubstitutor = TypeSubstitutor.create(
            constructor.typeParameters
                .withIndex()
                .associateBy({ it.value.typeConstructor }) {
                    TypeProjectionImpl(newTypeParameters[it.index])
                }
        )
        val substitutedConstructorDescriptor = constructor.substitute(typeSubstitutor) ?: return null
        val classWOTPType = substitutedConstructorDescriptor.returnType
        val generatedValueParams = generateValueParameters(substitutedConstructorDescriptor.valueParameters, depth)
        if (generatedValueParams.size != substitutedConstructorDescriptor.valueParameters.size) return null
        val name = classWOTPType.constructor.declarationDescriptor?.name ?: "$classWOTPType".substringBefore('<')
            .substringBefore('(')
        val numOfTP = constructor.typeParameters.size
        val typeArgs = classWOTPType.arguments.take(numOfTP)
            .let { if (it.isEmpty()) "" else it.joinToString(prefix = "<", postfix = ">") }
        val generatedExp =
            Factory.psiFactory.createExpressionIfPossible("$name$typeArgs(${generatedValueParams.joinToString()})")
        return generatedExp to classWOTPType
    }


}