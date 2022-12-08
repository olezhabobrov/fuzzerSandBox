package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.BugType

//class ProjectMultiCompilerTestChecker(
//    override val project: Project,
//    override var curFile: BBFFile,
//    private val compilers: List<CommonCompiler>
//) : MultiCompilerCrashChecker(project, curFile, null, BugType.DIFFBEHAVIOR) {
//
//}

//package com.stepanov.bbf.bugfinder.executor
//
//import com.intellij.psi.PsiErrorElement
//import com.intellij.psi.PsiFile
//import com.stepanov.bbf.bugfinder.executor.project.Project
//import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
//import com.stepanov.bbf.bugfinder.util.getFileLanguageIfExist
//import com.stepanov.bbf.reduktor.parser.PSICreator
//import com.stepanov.bbf.reduktor.util.getAllChildrenNodes
//
//class ProjectMultiCompilerTestChecker(
//    private val compiler: CommonCompiler,
//    var otherFiles: Project?,
//    //Often order of files affects bug
//    var filePos: Int
//) :
//    MultiCompilerCrashChecker(compiler) {
//
//    fun isAlreadyCheckedOrWrong(psiFiles: List<PsiFile>): Pair<Boolean, Boolean> {
//        val text = psiFiles.joinToString("\n") { it.text }
//        val hash = text.hashCode()
//        if (alreadyChecked.containsKey(hash)) {
//            log.debug("ALREADY CHECKED!!!")
//            return true to alreadyChecked[hash]!!
//        }
//        if (psiFiles.any { it.node.getAllChildrenNodes().any { it.psi is PsiErrorElement } }) {
//            log.debug("Not correct syntax")
//            alreadyChecked[hash] = false
//            return true to false
//        }
//        return false to false
//    }
//
//    override fun checkTest(text: String, pathToFile: String): Boolean = TODO()
////    {
////        var haveJava = false
////        if (otherFiles == null) return false
////        val psiFiles =
////            otherFiles!!.texts.let { it.subList(0, filePos) + listOf(text) + it.subList(filePos, it.size) }.map {
////                if (it.getFileLanguageIfExist() == LANGUAGE.KOTLIN) {
////                    PSICreator.getPSIForText(it, false)
////                } else {
////                    haveJava = true
////                    PSICreator.getPsiForJava(it, Factory.file.project)
////                }
////            }
////        //WE SHOULD'NT REMOVE COMMENT WITH FILE NAME
////        if (!text.contains(Regex("""((//\s*FILE)|(//\s*File\s*)).*(.java|.kt)\n"""))) return false
////
////        val firstCheck = isAlreadyCheckedOrWrong(psiFiles)
////        if (firstCheck.first) return firstCheck.second
////        val proj =
////            if (haveJava) {
////                Project(psiFiles.map { it.text }, null, LANGUAGE.KJAVA)
////            } else {
////                Project(psiFiles.map { it.text })
////            }
////        val path = proj.saveOrRemoveToTmp(true)
////        if (path.isEmpty()) return false
////        val res = compiler.isCompilerBug(path)
////        proj.saveOrRemoveToTmp(false)
////        alreadyChecked[text.hashCode()] = res
////        return res
////    }
//}