package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.reduktor.executor.error.Error
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

class DiffCompileChecker(
    override val project: Project,
    override var curFile: BBFFile,
    private val compilers: List<CommonCompiler>
) : MultiCompilerCrashChecker(project, curFile, null, BugType.DIFFBEHAVIOR) {

    override fun checkTest(): Boolean {
        val preCheck = isAlreadyCheckedOrWrong()
        if (preCheck.first) return preCheck.second
        if (compilers.any { it.isCompilerBug(project) }) {
            alreadyChecked[projectHash] = false
            return false
        }
        val res = isDiffCompile()
        alreadyChecked[projectHash] = res
        return res
    }

    private fun isDiffCompile(): Boolean = isDiffCompile(project)

    private fun isDiffCompile(project: Project): Boolean {
        val compilersToStatus = compilers.map { it to it.checkCompiling(project) }
        return compilersToStatus.map { it.second }.toSet().size != 1
    }

    @Deprecated("")
    override fun checkTest(text: String): Boolean {
        val tmpProject = Project.createFromCode(text)
        tmpProject.configuration = project.configuration
        val projectHash = text.trim().hashCode()
        val preCheck = isAlreadyCheckedOrWrong(projectHash, tmpProject.files.first())
        if (preCheck.first) return preCheck.second
        val res = isDiffCompile(tmpProject)
        alreadyChecked[projectHash] = res
        return res
    }
}