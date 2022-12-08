package com.stepanov.bbf.bugfinder.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackageStatement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parents
import java.nio.file.Files
import java.nio.file.Paths

object ProjectInstrumenter {

    fun instrument() {
        Files.walk(Paths.get("/home/zver/IdeaProjects/kotlin/compiler/backend/")).forEach {
            val file = it.toFile()
            if (file.name == "CoverageEntry.kt" || file.name == "MyMethodBasedCoverage.kt") return@forEach
            var lang = ""
            val psi = if ("$it".endsWith(".java")) {
                lang = "java"
                PSICreator.getPsiForJava(file.readText())
            } else if ("$it".endsWith(".kt")) {
                lang = "kotlin"
                PSICreator.getPSIForFile(it.toAbsolutePath().toString())
            } else return@forEach
            val newPsi = if (lang == "java") {
                val methods =
                    psi.getAllPSIChildrenOfType<PsiMethod>()
                        .filterNot { it.parents.any { it is PsiMethod } }
                val packageDirective =
                    psi.getAllPSIChildrenOfType<PsiPackageStatement>()
                        .firstOrNull()
                        ?.text
                        ?.substringAfter("package")
                        ?.substringBeforeLast(';')
                        ?.split(".")
                        ?: listOf()
                val factory = PsiElementFactory.getInstance(psi.project)
                for (m in methods) {
                    val parentClasses =
                        m.parents.toList().filterIsInstance<PsiClass>().mapNotNull { it.name }.reversed()
                    val parentClass = m.parents.firstOrNull { it is PsiClass } as? PsiClass
                    val isConstructorCall = parentClass != null && m.name == parentClass.name
                    if (isConstructorCall) continue
                    val methodName = m.name
                    val parentClassesStr =
                        if (parentClasses.isEmpty()) {
                            ""
                        } else {
                            "." + parentClasses.joinToString("\\\\$").trim()
                        }
                    val pathToMethod = packageDirective.joinToString(".").trim() + parentClassesStr
                    val params = m.parameters.map { "${it.type}".substringAfterLast("PsiType:").substringBefore('<') }
                        .joinToString(";")
                    val returnType =
                        m.returnType?.let { "${it}".substringAfter("PsiType:").substringBefore('<') } ?: "void"
                    println("METHOD = $pathToMethod NAME = $methodName PARAMS = $params RET = $returnType")
                    val body = m.body ?: continue
                    if (body.lBrace == null || body.rBrace == null) continue
                    body.lBrace!!.delete()
                    body.rBrace!!.delete()
                    val newText = """{
                    CoverageEntry covEn491 = new CoverageEntry("$pathToMethod", "$methodName", "$params", "$returnType");
                    MyMethodBasedCoverage.putEntry(covEn491);
                    ${body.text}
                }
                """.trimIndent()
                    val newBody = factory.createCodeBlockFromText(newText, null)
                    body.replaceThis(newBody)
                }
                val packageStatementPsi = psi.getAllPSIChildrenOfType<PsiPackageStatement>().firstOrNull()

                var newFileText = packageStatementPsi?.text ?: ""
                packageStatementPsi?.delete()
                newFileText +=
                    """

                    import coverage.CoverageEntry;
                    import coverage.MyMethodBasedCoverage;

                    ${psi.text}
                """.trimIndent()

                PSICreator.getPsiForJava(newFileText)
            } else {
                val ktFile = psi as KtFile
                val methods = ktFile.getAllPSIChildrenOfType<KtNamedFunction>()
                    .filterNot { it.parents.any { it is KtNamedFunction } }
                val packageDirective = ktFile.packageFqName.asString().split(".")
                for (m in methods) {
                    val methodName = m.name ?: continue
                    if (m.bodyExpression == null) continue
                    val parentClasses = m.parents.filterIsInstance<KtClassOrObject>().mapNotNull { it.name }.toList().reversed()
                    val parentClassesStr =
                        if (parentClasses.isEmpty()) {
                            ".${ktFile.name.substringBefore(".kt").substringAfterLast('/')}Kt"
                        } else {
                            "." + parentClasses.joinToString("\\$").trim()
                        }
                    val pathToMethod = packageDirective.joinToString(".").trim() + parentClassesStr
                    val params =
                        m.valueParameters.map { it.typeReference?.text?.substringBefore('<')?.substringBefore('?') ?: "Any" }.toMutableList()
                    if (m.receiverTypeReference != null) {
                        params.add(0, m.receiverTypeReference!!.text.substringBefore('<'))
                    }
                    val strParams = params.joinToString(";")
                    val reserveRtv = if (m.isUnit()) "Unit" else "Any"
                    val returnType = m.typeReference?.text?.substringBefore('<')?.substringBefore('?') ?: reserveRtv
                    println("METHOD = $pathToMethod NAME = $methodName PARAMS = $strParams RET = $returnType")
                    val insertion = """
                    val covEn153 = CoverageEntry("$pathToMethod", "$methodName", "$strParams", "$returnType");
                    MyMethodBasedCoverage.putEntry(covEn153);
                """.trimIndent()
                    if (m.bodyBlockExpression == null) {
                        val newBody = """
                        run {
                            $insertion
                            ${m.bodyExpression?.text}
                        }
                    """.trimIndent()
                        val newPsiBody = Factory.psiFactory.createExpression(newBody)
                        m.bodyExpression!!.replaceThis(newPsiBody)
                    } else {
                        val block = m.bodyBlockExpression!!
                        block.lBrace?.delete()
                        block.rBrace?.delete()
                        val newBlockPsi = Factory.psiFactory.createBlock(
                            """
                        $insertion
                        ${block.text}
                    """.trimIndent()
                        )
                        m.bodyBlockExpression!!.replaceThis(newBlockPsi)
                    }
                }
                psi.addImport("coverage.CoverageEntry", false)
                psi.addImport("coverage.MyMethodBasedCoverage", false)
                psi.copy() as KtFile
            }
            it.toFile().writeText(newPsi.text)
        }

    }
}