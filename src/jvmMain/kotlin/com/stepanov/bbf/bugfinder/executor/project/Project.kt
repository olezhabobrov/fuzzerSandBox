package com.stepanov.bbf.bugfinder.executor.project

import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.addMain
import com.stepanov.bbf.bugfinder.executor.addMainForPerformanceTesting
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.util.getAllWithout
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import org.jetbrains.kotlin.psi.KtFile

class Project(
    var configuration: Header,
    var files: List<BBFFile>,
    val language: LANGUAGE = LANGUAGE.KOTLIN
) {

    constructor(configuration: Header, file: BBFFile, language: LANGUAGE) : this(configuration, listOf(file), language)

    companion object {
        fun createFromCode(code: String): Project {
            val configuration = Header.createHeader(getCommentSection(code))
            val files = BBFFileFactory(code, configuration).createBBFFiles() ?: return Project(configuration, listOf())
            val language =
                when {
                    files.any { it.getLanguage() == LANGUAGE.UNKNOWN } -> LANGUAGE.UNKNOWN
                    files.any { it.getLanguage() == LANGUAGE.JAVA } -> LANGUAGE.KJAVA
                    else -> LANGUAGE.KOTLIN
                }
            return Project(configuration, files, language)
        }
    }

    fun addFile(file: BBFFile): List<BBFFile> {
        files = files + listOf(file)
        return files
    }

    fun removeFile(file: BBFFile): List<BBFFile> {
        files = files.getAllWithout(file)
        return files
    }

    fun saveOrRemoveToDirectory(trueSaveFalseDelete: Boolean, directory: String): String {
        files.forEach {
            val name = it.name.substringAfterLast('/')
            val fullDir = directory +
                    if (it.name.contains("/")) {
                        "/${it.name.substringBeforeLast('/')}"
                    } else {
                        ""
                    }
            val fullName = "$fullDir/$name"
            if (trueSaveFalseDelete) {
                File(fullDir).mkdirs()
                File(fullName).writeText(it.psiFile.text)
            } else {
                val createdDirectories = it.name.substringAfter(directory).substringBeforeLast('/')
                if (createdDirectories.trim().isNotEmpty()) {
                    File("$directory$createdDirectories").deleteRecursively()
                } else {
                    File(fullName).delete()
                }
            }
        }
        return files.joinToString(" ") { it.name }
    }

    fun saveOrRemoveToTmp(trueSaveFalseDelete: Boolean): String {
        files.forEach {
            if (trueSaveFalseDelete) {
                File(it.name.substringBeforeLast("/")).mkdirs()
                File(it.name).writeText(it.psiFile.text)
            } else {
                val createdDirectories = it.name.substringAfter(CompilerArgs.pathToTmpDir).substringBeforeLast('/')
                if (createdDirectories.trim().isNotEmpty()) {
                    File("${CompilerArgs.pathToTmpDir}$createdDirectories").deleteRecursively()
                } else {
                    File(it.name).delete()
                }
            }
        }
        return files.joinToString(" ") { it.name }
    }

    fun moveAllCodeInOneFile() =
        StringBuilder().apply {
            append(configuration.toString());
            if (configuration.isWithCoroutines())
                files.getAllWithoutLast().forEach { appendLine(it.toString()) }
            else files.forEach { appendLine(it.toString()) }
        }.toString()

    fun convertToSingleFileProject(): Project {
        if (this.files.size <= 1) return this.copy()
        if (this.language != LANGUAGE.KOTLIN) return this.copy()
        val configuration = this.configuration
        val language = this.language
        val projFiles = this.files.map { it.psiFile.copy() as KtFile }
        val resFile = projFiles.first().copy() as KtFile
        resFile.packageDirective?.delete()
        resFile.importDirectives.forEach { it.delete() }
        projFiles.getAllWithout(0).forEach {
            it.packageDirective?.delete()
            it.importList?.delete()
            resFile.addAtTheEnd(it)
        }
        StdLibraryGenerator.calcImports(resFile)
            .forEach { resFile.addImport(it.substringBeforeLast('.'), true) }
        if (this.configuration.isWithCoroutines()) {
            resFile.addImport("kotlin.coroutines.intrinsics", true)
            resFile.addImport("kotlin.coroutines.jvm.internal.CoroutineStackFrame", false)
        }
        val pathToTmp = CompilerArgs.pathToTmpDir
        val fileName = "$pathToTmp/tmp0.kt"
        return Project(configuration, BBFFile(fileName, Factory.psiFactory.createFile(resFile.text)), language)
    }

    fun saveInOneFile(pathToSave: String) {
        val text = moveAllCodeInOneFile()
        File(pathToSave).writeText(text)
    }


    fun isBackendIgnores(backend: String): Boolean = configuration.ignoreBackends.contains(backend)

    fun getProjectSettingsAsCompilerArgs(backendType: String): CommonCompilerArguments {
        val args = when (backendType) {
            "JVM" -> K2JVMCompilerArguments()
            else -> K2JSCompilerArguments()
        }
        val languageDirective = "-XXLanguage:"
        val languageFeaturesAsArgs = configuration.languageSettings.joinToString(
            separator = " $languageDirective",
            prefix = languageDirective,
        ).split(" ")
        when (backendType) {
            "JVM" -> args.apply {
                K2JVMCompiler().parseArguments(
                    languageFeaturesAsArgs.toTypedArray(),
                    this as K2JVMCompilerArguments
                )
            }
            "JS" -> args.apply {
                K2JSCompiler().parseArguments(
                    languageFeaturesAsArgs.toTypedArray(),
                    this as K2JSCompilerArguments
                )
            }
        }
        args.optIn = configuration.useExperimental.toTypedArray()
        return args
    }

    fun addMain(): Project {
        if (files.map { it.text }.any { it.contains("fun main(") }) return Project(configuration, files, language)
        val boxFuncs =
            files.flatMap { it.psiFile.getAllPSIChildrenOfType<KtNamedFunction> { it.name?.contains("box") == true } }
        if (boxFuncs.isEmpty()) return Project(configuration, files, language)
        val indOfFile =
            files.indexOfFirst {
                it.psiFile.getAllPSIChildrenOfType<KtNamedFunction>().any { it.name?.contains("box") == true }
            }
        if (indOfFile == -1) return Project(configuration, files, language)
        val file = files[indOfFile]
        val psiCopy = file.psiFile.copy() as PsiFile
        psiCopy.addMain(boxFuncs)
        val newFirstFile = BBFFile(file.name, psiCopy)
        val newFiles =
            listOf(newFirstFile) + files.getAllWithout(indOfFile).map { BBFFile(it.name, it.psiFile.copy() as PsiFile) }
        return Project(configuration, newFiles, language)
    }

    fun addMainAndExecBoxNTimes(times: Int): Project {
        if (files.map { it.text }.any { it.contains("fun main(") }) return Project(configuration, files, language)
        val boxFuncs =
            files.flatMap { it.psiFile.getAllPSIChildrenOfType<KtNamedFunction> { it.name?.contains("box") == true } }
        if (boxFuncs.isEmpty()) return Project(configuration, files, language)
        val indOfFile =
            files.indexOfFirst {
                it.psiFile.getAllPSIChildrenOfType<KtNamedFunction>().any { it.name?.contains("box") == true }
            }
        if (indOfFile == -1) return Project(configuration, files, language)
        val file = files[indOfFile]
        val psiCopy = file.psiFile.copy() as PsiFile
        psiCopy.addMainForPerformanceTesting(boxFuncs, times)
        val newFirstFile = BBFFile(file.name, psiCopy)
        val newFiles =
            listOf(newFirstFile) + files.getAllWithout(indOfFile).map { BBFFile(it.name, it.psiFile.copy() as PsiFile) }
        return Project(configuration, newFiles, language)
    }


    fun copy(): Project {
        return Project(configuration, files.map { it.copy() }, language)
    }


    override fun toString(): String = files.joinToString("\n\n") {
        it.name + "\n" +
                it.psiFile.text
    }
}