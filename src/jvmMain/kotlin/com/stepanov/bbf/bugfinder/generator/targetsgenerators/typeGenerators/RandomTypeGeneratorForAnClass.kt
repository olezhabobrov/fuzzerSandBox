package com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators

import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.types.KotlinType
import kotlin.random.Random


object RandomTypeGeneratorForAnClass {

    val file: KtFile
        get() = RandomTypeGenerator.file

    private fun generateEnumType(): String {
        val fromFile =
            file.getAllPSIChildrenOfType<KtClass>().filter { it.isEnum() && it.name != null }.randomOrNull()
        fromFile?.let { return it.name!! }
        val fromLib = StdLibraryGenerator.klasses
            .filter { it.kind == ClassKind.ENUM_CLASS && it.visibility.isPublicAPI }
        return fromLib.random().name.asString()
    }

    private fun generateAnnotationType(): String {
        val fromFile =
            file.getAllPSIChildrenOfType<KtClass>().filter { it.isAnnotation() && it.name != null }.randomOrNull()
        fromFile?.let { return it.name!! }
        val fromLib = StdLibraryGenerator.klasses
            .filter { it.kind == ClassKind.ANNOTATION_CLASS
                    && it.visibility.isPublicAPI
                    && !it.findPackage().toString().contains("js")
                    && !it.classId.toString().let { it.contains("Experimental") || it.contains("Opt") }}
        return fromLib.random().name.asString()
    }

    fun generateTypeForAnnotationClass(): String =
        when (Random.nextInt(0, 4)) {
            0 -> RandomTypeGenerator.signedPrimitives.random()
            1 -> "KClass<*>"
            2 -> generateEnumType()
            else -> generateAnnotationType()
        }

    fun generateTypeForAnnotationClassAsKType(): KotlinType? =
        RandomTypeGenerator.generateType(generateTypeForAnnotationClass())

}