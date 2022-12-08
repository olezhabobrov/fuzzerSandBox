package com.stepanov.bbf.bugfinder.util

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.compilers.JSCompiler
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import org.apache.log4j.Logger
import java.io.File

class FalsePositivesDeleter {

    private val compilers: MutableList<CommonCompiler> = mutableListOf()
    private val log = Logger.getLogger("bugFinderLogger")

    init {
        val compilersConf = BBFProperties.getStringGroupWithoutQuotes("BACKENDS")
        compilersConf.filter { it.key.contains("JVM") }.forEach { compilers.add(
            JVMCompiler(
                it.value
            )
        ) }
        compilersConf.filter { it.key.contains("JS") }.forEach { compilers.add(
            JSCompiler(
                it.value
            )
        ) }
    }

    fun cleanDirs() {
//        for (compiler in compilers) {
//            val resDir = CompilerArgs.resultsDir
//            val compilerBugsDir = compiler.compilerInfo.filter { it != ' ' }
//            val dirWithPotentialDuplicates =
//                    if (resDir.endsWith("/")) "$resDir$compilerBugsDir/"
//                    else "$resDir/$compilerBugsDir/"
//            if (!File(dirWithPotentialDuplicates).exists()) continue
//            for (file in File(dirWithPotentialDuplicates).listFiles()) {
//                if (file.absolutePath.contains("PROJECT")) continue
//                log.debug("Checking ${file.path} for ${compiler.compilerInfo}")
//                if (!compiler.isCompilerBug(file.absolutePath)) {
//                    log.debug("Removing ${file.absolutePath} because it's not a bug anymore\nText: ${file.readText()}")
//                    file.delete()
//                }
//            }
//        }
    }

}