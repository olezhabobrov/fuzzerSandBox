package com.stepanov.bbf.bugfinder.executor.checkers

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.Header
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.reduktor.parser.PSICreator

//class AbstractTreeMutator(private val compilers: List<CommonCompiler>, val configuration: Header) {
//    constructor(compiler: CommonCompiler, configuration: Header) : this(listOf(compiler), configuration)

//    private fun prepareProject(newFile: PsiFile, originalFile: BBFFile) {
//        originalFile.changePsiFile(newFile, false)
//    }
//    private fun prepareChecker(newFile: PsiFile, originalFile: PsiFile): MutationChecker {
//        val tmpName = "${CompilerArgs.pathToTmpDir}/tmp.kt"
//        val bbfFile = BBFFile(tmpName, file)
//        val pr = Project(configuration, listOf(bbfFile), LANGUAGE.KOTLIN)
//        return MutationChecker(compilers, pr, pr.files.first())
//    }

//    fun replacePSINodeIfPossible(file: PsiFile, node: PsiElement, replacement: PsiElement) =
//        prepareChecker(file).replacePSINodeIfPossible(node, replacement)
//
//    fun replaceNodeIfPossible(file: PsiFile, node: ASTNode, replacement: ASTNode) =
//        prepareChecker(file).replaceNodeIfPossible(node, replacement)
//
//    fun replaceNodeIfPossibleWithNodeWithFileReplacement(newFile: PsiFile, originalFile: BBFFile, node: ASTNode, replacement: ASTNode) {
//        prepareProject(newFile, originalFile)
//        try {
//
//        }
//    }

//    fun addNodeIfPossible(file: PsiFile, anchor: PsiElement, node: PsiElement, before: Boolean = false) =
//        prepareChecker(file).addNodeIfPossible(anchor, node, before)
//
//    fun checkCompiling(file: PsiFile) = prepareChecker(file).checkCompiling()

//    fun addNodeIfPossibleWithNode(
//        file: PsiFile,
//        anchor: PsiElement,
//        node: PsiElement,
//        before: Boolean = false
//    ): PsiElement? = prepareChecker(file).addNodeIfPossibleWithNode(anchor, node, before)
//}