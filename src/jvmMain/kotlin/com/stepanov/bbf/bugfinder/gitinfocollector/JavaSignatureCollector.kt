package com.stepanov.bbf.bugfinder.gitinfocollector

import com.intellij.psi.*
import com.stepanov.bbf.reduktor.util.getAllChildren
import coverage.CoverageEntry
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class JavaSignatureCollector : SignatureCollectorInterface<PsiMethod> {

    override fun collect(funcs: List<PsiMethod>): List<CoverageEntry> {
        if (funcs.isEmpty()) return listOf()
        return funcs.map { f ->
            val psiFile = f.parents.last() as PsiFile
            val packageDirective =
                psiFile.getAllChildren()
                    .firstOrNull { it is PsiPackageStatement }
                    ?.text
                    ?.substringAfter("package ")
                    //?.replace('.', '/')
                    ?.dropLast(1) ?: ""
            val path =
                f.parents
                    .filter { it is PsiClass || it is PsiMethod }
                    .map { (it as PsiNamedElement).name }
                    .filter { it != null && it.trim().isNotEmpty() }
                    .toList().reversed()
                    .joinToString("\$")
            val name = f.name ?: ""
//            val args = f.parameters.let {
//                if (it.isEmpty()) "()"
//                else it.joinToString(separator = ";", prefix = "(", postfix = ";)") {
//                    it.type.toString().substringAfter("PsiType:").substringBefore('<')
//                }
//            }
            val params = f.parameters.map { it.type.toString().substringAfter("PsiType:").substringBefore('<') }
            val rtv =
                f.returnTypeElement?.type?.toString()?.substringAfter("PsiType:")?.substringBefore('<') ?: "void"
            //"$packageDirective/$path:$name$args$rtv;"
            CoverageEntry("$packageDirective/$path", name, params, rtv)
        }
    }

}