package com.stepanov.bbf.bugfinder.util.decompiler

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.meta.PsiMetaData
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addImport
import com.stepanov.bbf.bugfinder.util.getAllChildrenNodes
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.zip.ZipFile
import kotlin.system.exitProcess

object K2JConverter {

    fun convertClassToJava(el: KtClassOrObject, checker: MutationChecker): PsiElement? {
        val project = checker.project
        val pathToDecompiled = "tmp/decompiled/"
        File(pathToDecompiled).deleteRecursively()
        File(pathToDecompiled).mkdirs()
        val tmpName = "tmp/tmp_for_converter.kt"
        val newFile = Factory.psiFactory.createFile("")
        val addedEl = newFile.addAtTheEnd(el)
        StdLibraryGenerator.calcImports(newFile)
            .forEach { newFile.addImport(it.substringBeforeLast('.'), true) }
        val bbfFile = BBFFile(tmpName, newFile)
        project.addFile(bbfFile)
        val compiled = JVMCompiler().compile(project, false)
        project.removeFile(bbfFile)
        if (compiled.status == -1) return null
        val savedSource = File(tmpName).writeText(el.text)
        val pathToJar = compiled.pathToCompiled
        ConsoleDecompiler.main(arrayOf(tmpName, pathToJar, pathToDecompiled))
        val zipFile = ZipFile("${pathToDecompiled}/tmp.jar")
        zipFile.copyContentTo(pathToDecompiled) { it.name.endsWith(".java") }
        val decompiledClass = File("$pathToDecompiled/${el.name}.java")
        if (!decompiledClass.exists()) return null
        val decompiledPsi = PSICreator.getPsiForJava("""
            ${decompiledClass.readText()}
        """.trimIndent())
        val decompiledPsiClass = decompiledPsi.getAllPSIChildrenOfType<PsiClass>().firstOrNull()
        decompiledPsiClass?.let { deleteExceedInfo(it) }
        return decompiledPsiClass
    }

    fun deleteExceedInfo(el: PsiElement) {
        el.node.getAllChildrenNodes()
            .filter { it.psi is PsiAnnotation && it.text.contains("@Metadata") }
            .forEach { it.psi.delete() }
        el.node.getAllChildrenNodes()
            .filter { it.psi is PsiExpressionStatement && it.text.contains("Intrinsics.") }
            .forEach { it.psi.delete() }
    }
//    fun convert(pathToFile: String, isProject: Boolean): String {
//        val pathToDecompiled = "tmp/decompiled/"
//        File(pathToDecompiled).deleteRecursively()
//        File(pathToDecompiled).mkdirs()
//        val file = File(pathToFile)
//        val compiled = JVMCompiler().compile(file.absolutePath, false)
//        if (compiled.status == -1) return ""
//        val pathToJar = compiled.pathToCompiled
//        ConsoleDecompiler.main(arrayOf(file.absolutePath, pathToJar, pathToDecompiled))
//        val zipFile = ZipFile("${pathToDecompiled}/tmp.jar")
//        zipFile.copyContentTo(pathToDecompiled) { it.name.endsWith(".java") }
//        return try {
//            val resPath =
//                if (isProject) handleProjectDecompiledFiles(pathToDecompiled)
//                else handleSingleDecompiledFile(pathToDecompiled)
//            resPath
//        } catch (e: Exception) {
//            println("Exception $e")
//            ""
//        }
//    }

//    private fun handleProjectDecompiledFiles(path: String): String {
//        val files = File(path).listFiles()?.toList() ?: return ""
//        val mainFile = files.find { it.absolutePath.endsWith("Main.kt") } ?: return ""
//        val javaFiles = files.filter { it.absolutePath.endsWith(".java") && !it.absolutePath.contains("Main") }
//        val mainPsi = PSICreator.getPSIForText(mainFile.readText())
//        val javaPsi = javaFiles.map { PSICreator.getPsiForJava(it.readText(), mainPsi.project) }
//        //Remove metadata and intrinsics
//        for (psiFile in javaPsi) {
//            psiFile.node.getAllChildrenNodes()
//                .filter { it.psi is PsiAnnotation && it.text.contains("@Metadata") }
//                .forEach { it.psi.delete() }
//            psiFile.node.getAllChildrenNodes()
//                .filter { it.psi is PsiExpressionStatement && it.text.contains("Intrinsics.") }
//                .forEach { it.psi.delete() }
//        }
//        //Remove classes from main
//        mainPsi.getAllPSIChildrenOfType<KtClassOrObject>().filter { it.isTopLevel() }.forEach { it.delete() }
//        //Add main
//        mainPsi.addMain(mainPsi.getAllPSIChildrenOfType<KtNamedFunction>().filter { it.name?.contains("box") ?: false })
//        //Save new files
//        mainFile.writeText(mainPsi.text)
//        javaFiles.forEachIndexed { index, file -> file.writeText(javaPsi[index].text) }
//        return (listOf(mainFile.absolutePath) + javaFiles.map { it.absolutePath }).joinToString(" ")
//    }
//
//    private fun handleSingleDecompiledFile(path: String): String {
//        val files = File(path).listFiles()?.toList() ?: return ""
//        val javaFiles = files.filter { it.absolutePath.endsWith(".java") }
//        val project = PSICreator.getPSIForText("").project
//        val javaPsi = javaFiles.map { PSICreator.getPsiForJava(it.readText(), project) }
//        for (psiFile in javaPsi) {
//            psiFile.node.getAllChildrenNodes()
//                .filter { it.psi is PsiAnnotation && it.text.contains("@Metadata") }
//                .forEach { it.psi.delete() }
//            psiFile.node.getAllChildrenNodes()
//                .filter { it.psi is PsiExpressionStatement && it.text.contains("Intrinsics.") }
//                .forEach { it.psi.delete() }
//        }
//        javaFiles.forEachIndexed { index, file -> file.writeText(javaPsi[index].text) }
//        return javaFiles.map { it.absolutePath }.joinToString(" ")
//    }

}