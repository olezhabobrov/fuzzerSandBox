package com.stepanov.bbf.bugfinder.gitinfocollector

import com.stepanov.bbf.bugfinder.util.isUnit
import coverage.CoverageEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class KotlinSignatureCollector : SignatureCollectorInterface<KtNamedFunction> {
    override fun collect(funcs: List<KtNamedFunction>): List<CoverageEntry> {
        if (funcs.isEmpty()) return listOf()
        return funcs.map { f ->
            val ktFile = f.parents.lastOrNull() as KtFile
            val packageDirective = ktFile.packageFqName.asString()//.replace('.', '/')
            var path = f.parents
                .filter { it is KtNamedFunction || it is KtClassOrObject }
                .map { (it as KtNamedDeclaration).name }
                .filter { it != null && it.trim().isNotEmpty() }
                .toList().reversed()
                .joinToString("\$")
            if (f.parents.all { it !is KtClassOrObject }) {
                val fileNameToClassName = ktFile.name.substringAfterLast('/').replace(".kt", "Kt")
                path =
                    if (path.isEmpty()) fileNameToClassName
                    else "$fileNameToClassName\$$path"
            }
            val name = f.name ?: ""
            val params = f.valueParameters
                .map { it.typeReference?.text?.substringBefore('<')?.substringBefore('?') ?: "Any" }
                .toMutableList()
            f.receiverTypeReference?.let { params.add(0, it.text) }
            val reserveRtv = if (f.isUnit()) "Unit" else "Any"
            val rtv = f.typeReference?.text?.substringBefore('<')?.substringBefore('?') ?: reserveRtv
            CoverageEntry("$packageDirective.$path", name, params, rtv)
        }
    }

}