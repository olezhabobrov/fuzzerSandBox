package com.stepanov.bbf.bugfinder.reflektTester

import com.stepanov.bbf.bugfinder.executor.compilers.ReflektCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import java.io.File
import java.util.*
import kotlin.system.exitProcess

object ReflektTester {
    fun test() {
        var fl = false
        for (f in File("tmp/arrays/").listFiles()) {
            try {
                if (f.name == "functionFromStdlibMultiFileFacade.kt") {
                    fl = true
                }
                if (!fl) continue
                println(f.name)
                val project = Project.createFromCode(f.readText())
                if (project.language != LANGUAGE.KOTLIN) continue
                //project.saveOrRemoveToDirectory(true, "tmp/testProject/examples/src/main/kotlin/io/reflekt/example/")
                val mainFile = QueryGenerator(project).generateQuery() ?: continue
                project.addFile(BBFFile("Main.kt", mainFile))
                println("EXECUTING PROJECT")
                val execRes = ReflektCompiler.getExecutionResult("tmp/testProject/examples", project)
                println(execRes)
                if (execRes.contains("CompilationException")) {
                    println("BUG!! Project: $project")
                    val lastDirectoryNumber =
                        File("/home/zver/IdeaProjects/bbfgradle/tmp/testProject/bugs/").listFiles().toList()
                            .map { it.name.toInt() }
                            .maxOrNull()!!
                    File("tmp/testProject/bugs/${lastDirectoryNumber + 1}/").mkdir()
                    project.saveOrRemoveToDirectory(true, "tmp/testProject/bugs/${lastDirectoryNumber + 1}")
                }
            }catch (e: Exception) {
                continue
            } catch (e: Error) {
                continue
            }
        }
        exitProcess(0)
    }
}