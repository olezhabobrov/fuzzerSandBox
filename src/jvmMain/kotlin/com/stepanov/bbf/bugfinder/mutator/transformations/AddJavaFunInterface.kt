package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomClassGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomInterfaceGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.addImport
import com.stepanov.bbf.bugfinder.util.decompiler.K2JConverter
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import kotlin.system.exitProcess

class AddJavaFunInterface: Transformation() {

    private val ktFile = file as KtFile
    private val ctx = PSICreator.analyze(ktFile)
    private val randomValueGenerator = RandomInstancesGenerator(ktFile, ctx!!)

    override fun transform() {
        if (ctx == null) return
        val klassGenerator = RandomInterfaceGenerator(ktFile, ctx)
        val initStr = GClass()
        initStr.classWord = "interface"
        initStr.modifiers = mutableListOf("fun")
        val generatedFunInterface = (klassGenerator.finishGeneration(initStr) as? KtClassOrObject) ?: return
        println("GEN = ${generatedFunInterface.text}")
        val decompiled = K2JConverter.convertClassToJava(generatedFunInterface, checker) ?: return
        val newFile = PSICreator.getPsiForJava("import org.jetbrains.annotations.*;\n\n" + decompiled.text) as PsiFile
        println("CONVERTED = ${newFile.text}")
        val fileName = "${CompilerArgs.pathToTmpDir}/${generatedFunInterface.name}.java"
        val newBBFFile = BBFFile(fileName, newFile)
        checker.project.addFile(newBBFFile)
        if (!checker.checkCompiling()) {
            checker.project.removeFile(newBBFFile)
        }
    }
}