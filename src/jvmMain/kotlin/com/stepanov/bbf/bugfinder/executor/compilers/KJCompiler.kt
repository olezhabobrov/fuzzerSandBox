package com.stepanov.bbf.bugfinder.executor.compilers

import com.stepanov.bbf.bugfinder.util.decompiler.copyContentTo
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.CompilationResult
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.Stream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider


class KJCompiler(override val arguments: String = "") : JVMCompiler(arguments) {
    override val compilerInfo: String
        get() = "KJVM $arguments"

    override var pathToCompiled: String = "tmp/tmp.jar"

    override fun compile(project: Project, includeRuntime: Boolean): CompilationResult {
        val projectWithMain = project.addMain()
        val kotlinCompiled = super.compile(projectWithMain, includeRuntime)
        if (kotlinCompiled.status == -1) return CompilationResult(-1, "")
        val path = projectWithMain.saveOrRemoveToTmp(true)
        val kotlinJar = ZipFile(kotlinCompiled.pathToCompiled, Charset.forName("CP866"))
        kotlinJar.copyContentTo(pathToTmpDir)
        val javaRes = compileJava(path, pathToTmpDir)
        File(kotlinCompiled.pathToCompiled).let { if (it.exists()) it.delete() }
        projectWithMain.saveOrRemoveToTmp(false)
        return if (javaRes) {
            CompilationResult(0, pathToTmpDir)
        } else {
            CompilationResult(-1, "")
        }
    }

    fun compileAndSaveInJar(project: Project, includeRuntime: Boolean = true): String {
        val path = compile(project, includeRuntime)
        val jar = JarOutputStream(FileOutputStream(pathToCompiled))//File(pathToCompiled)
        for (f in File(path.pathToCompiled).listFiles().filter { it.isFile }) {
            println(f.name)
            jar.putNextEntry(ZipEntry(f.name))
            val t = f.readText(Charset.forName("CP866")).toByteArray(Charset.forName("CP866"))
            //println("T = $t")
            jar.write(t, 0, t.size)
            jar.closeEntry()
        }
        jar.close()
        return pathToCompiled
    }

    private fun compileJava(path: String, pathToDir: String): Boolean {
        val javaFiles = path.split(" ").filter { it.endsWith(".java") }.map { File(it) }
        if (javaFiles.isEmpty()) return true
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val manager = compiler.getStandardFileManager(diagnostics, null, null)
        val sources = manager.getJavaFileObjectsFromFiles(javaFiles)
        val classPath = (CompilerArgs.jvmStdLibPaths + CompilerArgs.getAnnoPath("13.0") + pathToDir).joinToString(":")
        val options = mutableListOf("-classpath", classPath, "-d", pathToDir)
        val task = compiler.getTask(null, manager, diagnostics, options, null, sources)
        task.call()
        return diagnostics.diagnostics.none { it.kind == Diagnostic.Kind.ERROR }
    }

//    override fun checkCompiling(pathToFile: String): Boolean {
//        val status = compile(pathToFile, false).status
//        File(pathToTmpDir).deleteRecursively()
//        return status == 0
//    }

    override fun exec(path: String, streamType: Stream, mainClass: String): String {
        val manifest =
            Files.walk(Paths.get(path))
                .toArray()
                .map { (it as Path).toFile() }
                .find { it.name == "MANIFEST.MF" }
                ?: return ""
        val mc =
            mainClass.ifEmpty {
                manifest.readLines().find { it.startsWith("Main-Class:") }?.substringAfter("Main-Class: ") ?: return ""
            }
        return commonExec("java -cp $path $mc", streamType)
    }

    val pathToTmpDir = CompilerArgs.pathToTmpFile.substringBeforeLast('/') + "/tmp/tmp"
}