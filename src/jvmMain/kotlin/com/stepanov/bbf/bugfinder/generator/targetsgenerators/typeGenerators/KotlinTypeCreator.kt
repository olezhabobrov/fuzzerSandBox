package com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType

object KotlinTypeCreator {

    fun createType(file: KtFile, type: String): KotlinType? {
        val vtype =
            if (type.contains('<')) {
                val substringBeforeDot = type.substringBefore('<').substringBeforeLast('.')
                //TODO!! Rewrite this!!
                if (substringBeforeDot.split(".").all { it[0].isUpperCase() }) {
                    type
                } else {
                    type.substringBefore('<').substringAfterLast('.') + "<" + type.substringAfter('<')
                }
            }
            else {
                type
            }
        if (vtype.contains("??") || vtype.contains("ERROR")) return null
        val fileCopy = file.copy() as KtFile
        val prefix = fileCopy.packageDirective?.text + "\n" + fileCopy.importList?.text + "\n"
        fileCopy.packageDirective?.delete()
        fileCopy.importList?.delete()
        val classDef =
            splitTypeByCommas(vtype)
                .mapIndexed { index, s ->
                    val twb = s.substringBefore(":").trim()
                    when {
                        file.getAllPSIChildrenOfType<KtClassOrObject>().all { it.name != twb } -> s
                        StdLibraryGenerator.findPackageForType(twb) != null -> s
                        !s.contains(':') -> "AUX$index: $s"
                        else -> s
                    }
                }
                .joinToString(prefix = "${vtype.substringBefore('<')}<", postfix = ">")
        val klass =
            try {
                val kl = Factory.psiFactory.createClass("class $classDef")
                if (kl.text != "class $classDef") null else kl
            } catch (e: Exception) {
                null
            }
        var typeParams = ""
        var typeParamsWithoutBounds = ""
        if (!vtype.contains("<")) {
            val typeReference = Factory.psiFactory.createTypeIfPossible(vtype)
            if (typeReference != null) {
                typeParams =
                    typeReference.getAllChildren()
                        .filter { it is KtUserType && !it.text.contains('<') }
                        .map { it.text.trim() }
                        .filter { ta -> file.getAllPSIChildrenOfType<KtClassOrObject>().all { it.name != ta } }
                        .filter { ta -> StdLibraryGenerator.findPackageForType(ta) == null }
                        .let { if (it.isEmpty()) "" else it.joinToString(prefix = "<", postfix = ">") }
                if (klass?.name == null) typeParamsWithoutBounds = vtype
            } else {
                typeParams = ""
                val t = vtype.substringBeforeLast('?').trim()
                if (file.getAllPSIChildrenOfType<KtClass>().all { it.name != t } &&
                    StdLibraryGenerator.findPackageForType(t) == null) {
                    typeParams = "<$t>"
                }
                if (klass?.name == null) typeParamsWithoutBounds = vtype
            }
        } else if (klass == null || klass.name == null) {
            val typeReference = Factory.psiFactory.createTypeIfPossible(vtype) ?: return null
            typeParams =
                typeReference.getAllChildren()
                    .filter { it is KtUserType && !it.text.contains('<') }
                    .map { it.text.trim() }
                    .filter { ta -> file.getAllPSIChildrenOfType<KtClassOrObject>().all { it.name != ta } }
                    .filter { ta -> StdLibraryGenerator.findPackageForType(ta) == null }
                    .let { if (it.isEmpty()) "" else it.joinToString(prefix = "<", postfix = ">") }
            typeParamsWithoutBounds = vtype
        } else {
            typeParams =
                klass.typeParameterList?.parameters
                    ?.filter { it.text.trim().isNotEmpty() }
                    ?.filter { it.text != "*" }
                    ?.filterNot { it.name?.contains("AUX") == true }
                    ?.filter { ta -> file.getAllPSIChildrenOfType<KtClassOrObject>().all { it.name != ta.name } }
                    ?.filter { ta -> StdLibraryGenerator.findPackageForType(ta.text) == null }
                    ?.let { if (it.isEmpty()) "" else it.joinToString(prefix = "<", postfix = ">") { it.text } } ?: ""
            typeParamsWithoutBounds =
                klass.typeParameterList?.parameters
                    ?.let {
                        if (it.isEmpty()) ""
                        else it.joinToString(prefix = "<", postfix = ">") {
                            if (it.text.contains("AUX")) it.text.substringAfter(':').trim()
                            else it.text.substringBefore(':').trim()
                        }
                    }
                    ?: ""
            //if (typeParams.trim().isEmpty()) typeParams = typeParamsWithoutBounds
        }
        val n = klass?.name ?: ""
        val func = "fun $typeParams abcq(a: $n$typeParamsWithoutBounds){}"
        val prop = "val abcq1: $n$typeParamsWithoutBounds = TODO()"
        val newFile = Factory.psiFactory.createFile("$prefix\n\n$prop\n\n$func\n\n${fileCopy.text}")
        val psiFunc = newFile.getAllPSIChildrenOfType<KtNamedFunction>().firstOrNull() ?: return null
        val psiProp = newFile.getAllPSIChildrenOfType<KtProperty>().firstOrNull() ?: return null
        val funTypeReference =
            psiFunc.valueParameters.firstOrNull()?.typeReference
                ?.let { listOf(it) + it.getAllPSIChildrenOfType<KtReferenceExpression>() }
                ?: listOf()
        val propTypeReference = psiProp.typeReference
            ?.let { listOf(it) + it.getAllPSIChildrenOfType<KtReferenceExpression>() }
            ?: listOf()
        val allTypeReferences =
            (funTypeReference + propTypeReference)
                .map { it.text }
                .toSet()
                .toList()
        //Search for package of type
        StdLibraryGenerator.findPackagesForTypes(allTypeReferences)
            .filter { "$it".contains("java") || "$it".contains("kotlin") }
            .filterDuplicatesBy { "$it" }
            .forEach { pack ->
                val import = Factory.psiFactory.createImportDirective(ImportPath(FqName(pack.fqName.toUnsafe()), true))
                newFile.addImport(import)
            }
        val ctx = PSICreator.analyze(newFile) ?: return null
        val propType = psiProp.typeReference?.getAbbreviatedTypeOrType(ctx)
        if (propType != null && !propType.isErrorType()) return propType
        return psiFunc.valueParameters.firstOrNull()?.typeReference?.getAbbreviatedTypeOrType(ctx)
    }


    private fun splitTypeByCommas(type: String): List<String> {
        val typeArgs = type.substringAfter('<').substringBeforeLast('>')
        if (typeArgs == type || typeArgs.isEmpty()) return listOf()
        val splitTypes = mutableListOf<String>()
        val handledType = StringBuilder()
        var brackets = 0
        for (ch in typeArgs) {
            if (ch == ',' && brackets == 0) {
                splitTypes.add(handledType.toString())
                handledType.clear()
            } else {
                if (ch == '<') brackets++
                if (ch == '>') brackets--
                handledType.append(ch)
            }
        }
        splitTypes.add(handledType.toString())
        return splitTypes
    }

    fun recreateType(file: KtFile, type: KotlinType): KotlinType? = createType(file, type.getNameWithoutError())
    fun recreateType(file: KtFile, type: KtTypeReference): KotlinType? = createType(file, type.text)

}