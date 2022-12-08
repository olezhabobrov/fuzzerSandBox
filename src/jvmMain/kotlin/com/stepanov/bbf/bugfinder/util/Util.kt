package com.stepanov.bbf.bugfinder.util

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.reduktor.executor.CompilerTestChecker
import com.stepanov.bbf.reduktor.util.replaceReturnValueTypeOnUnit
import com.stepanov.bbf.reduktor.util.replaceThis
import org.jetbrains.kotlin.cli.jvm.compiler.CompileEnvironmentException
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.PathUtil.kotlinPathsForCompiler
import java.io.*
import java.lang.Exception
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream

enum class Stream {
    INPUT, ERROR, BOTH
}

fun Process.readStream(type: Stream): String {
    val res = StringBuilder()
    val reader = when (type) {
        Stream.INPUT -> BufferedReader(InputStreamReader(this.inputStream))
        Stream.ERROR -> BufferedReader(InputStreamReader(this.errorStream))
        else -> BufferedReader(InputStreamReader(SequenceInputStream(this.inputStream, this.errorStream)))
    }
    if (!reader.ready()) return ""
    var line: String? = reader.readLine()
    while (line != null) {
        res.append("$line\n")
        line = reader.readLine()
    }
    reader.close()
    return res.toString()
}

fun Process.readInputAndErrorStreams(): String = readStream(Stream.INPUT) + readStream(
    Stream.ERROR
)

fun KtExpression.getType(ctx: BindingContext): KotlinType? {
    val typesOfExpressions = this.getAllPSIChildrenOfType<KtExpression>().map { ctx.getType(it) }.filterNotNull()
    val typeReferences =
        this.getAllPSIChildrenOfType<KtTypeReference>().map { it.getAbbreviatedTypeOrType(ctx) }.filterNotNull()
    return when {
        typesOfExpressions.isNotEmpty() -> typesOfExpressions.first()
        typeReferences.isNotEmpty() -> typeReferences.first()
        else -> null
    }
}


fun KtNamedFunction.initBodyByValue(psiFactory: KtPsiFactory, value: String) {
    if (!this.hasBody()) {
        return
    } else if (this.hasBlockBody()) {
        if (this.typeReference == null) {
            replaceReturnValueTypeOnUnit(psiFactory)
            this.node.removeChild(this.bodyExpression!!.node)
            this.add(psiFactory.createEmptyBody())
            return
        }
        val eq = psiFactory.createEQ()
        val space = psiFactory.createWhiteSpace(" ")
        val valExp = psiFactory.createExpression(value)
        this.node.removeChild(this.bodyExpression!!.node)
        this.add(eq)
        this.add(space)
        this.add(valExp)
    } else {
        val valExp = psiFactory.createExpression(value)
        this.bodyExpression!!.replaceThis(valExp)
    }
}

fun PsiElement.find(el: PsiElement): PsiElement? = this.node.getAllChildrenNodes().find { it.psi == el }?.psi

//Returns true if all compilers compiling
fun List<CommonCompiler>.checkCompilingForAllBackends(project: Project): Boolean =
    this.map { it.checkCompiling(project) }.all { it }


fun writeRuntimeToJar(lib: String, stream: JarOutputStream) {
    val stdlibPath = File(lib)
    if (!stdlibPath.exists()) {
        throw CompileEnvironmentException("Couldn't find kotlin-stdlib at $stdlibPath")
    }
    copyJarImpl(stream, stdlibPath)
}

fun copyJarImpl(stream: JarOutputStream, jarPath: File) {
    JarInputStream(FileInputStream(jarPath)).use { jis ->
        while (true) {
            val e = jis.nextJarEntry ?: break
            if (FileUtilRt.extensionEquals(e.name, "class")) {
                try {
                    stream.putNextEntry(e)
                    FileUtil.copy(jis, stream)
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }
}

fun copyFullJarImpl(stream: JarOutputStream, jarPath: File) {
    JarInputStream(FileInputStream(jarPath)).use { jis ->
        while (true) {
            val e = jis.nextJarEntry ?: break
            stream.putNextEntry(e)
            FileUtil.copy(jis, stream)
        }
    }
}