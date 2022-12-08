package com.stepanov.bbf.bugfinder.executor.project

import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.util.addImport
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory
import com.stepanov.bbf.bugfinder.util.flatMap


//fun Project.moveAllCodeInOneFile(): Project {
//    val files = this.files.map { BBFFile(it.name, it.psiFile.copy() as PsiFile) }
//    val fileWithImports = psiFactory.createFile("")
//    val packages = files
//        .flatMap { it.psiFile.getAllPSIChildrenOfType<KtPackageDirective>() }
//    // Move all imports in one file
//    val imports = files
//        .flatMap { it.psiFile.getAllPSIChildrenOfType<KtImportDirective>() }
//        .map { it.importPath.toString() }
//        .toSet()
//        //TODO!!!
//        .filter { it.contains("kotlin") || it.contains("java") }
//
//    imports.map {
//        if (it.contains('*'))
//            psiFactory.createImportDirective(ImportPath(FqName(it.takeWhile { it != '*' }), true))
//        else
//            psiFactory.createImportDirective(ImportPath(FqName(it), false))
//    }.forEach { fileWithImports.addImport(it) }
//    files.map { it.psiFile.getAllPSIChildrenOfType<KtPackageDirective>().forEach { it.delete() } }
//    val text = files.joinToString("\n") { it.psiFile.text }
//    val resFile = psiFactory.createFile(fileWithImports.text + "\n" + text)
//    return Project.createFromCode(resFile.text)
//}