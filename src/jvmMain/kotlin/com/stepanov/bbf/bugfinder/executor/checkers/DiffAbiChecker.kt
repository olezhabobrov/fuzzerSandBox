package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.BugType

class DiffAbiChecker (
    override val project: Project,
    override var curFile: BBFFile,
    private val compilers: List<CommonCompiler>
) : MultiCompilerCrashChecker(project, curFile, null, BugType.DIFFBEHAVIOR) {

    companion object {
        var td = 0
    }

//    override fun checkTest(): Boolean {
//
//    }

}

