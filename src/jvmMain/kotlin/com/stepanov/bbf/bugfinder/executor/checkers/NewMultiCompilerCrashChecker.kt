@file:Suppress("DEPRECATION")

package com.stepanov.bbf.bugfinder.executor.checkers

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.TreeElement
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.reduktor.executor.CompilerTestChecker
import com.stepanov.bbf.reduktor.util.*
import org.apache.log4j.Logger

open class MultiCompilerCrashChecker(
    override val project: Project,
    override var curFile: BBFFile,
    val compiler: CommonCompiler?,
    val bugType: BugType
) : CompilerTestChecker {

    override fun removeNodeIfPossible(node: ASTNode): Boolean {
        val tmp = Factory.psiFactory.createWhiteSpace("\n")
        return replaceNodeIfPossible(node, tmp.node)
    }

    override fun removeNodesIfPossible(nodes: List<ASTNode>): Boolean {
        val copies = mutableListOf<ASTNode>()
        val whiteSpaces = mutableListOf<ASTNode>()
        nodes.forEach { copies.add(it.copyElement()); whiteSpaces.add(Factory.psiFactory.createWhiteSpace("\n").node) }
        for ((i, node) in nodes.withIndex()) {
            for (p in node.getAllParentsWithoutNode()) {
                try {
                    p.replaceChild(node, whiteSpaces[i])
                    break
                } catch (e: AssertionError) {
                }
            }
        }
        if (!checkTest()) {
            for ((i, node) in nodes.withIndex()) {
                for (p in whiteSpaces[i].getAllParentsWithoutNode()) {
                    try {
                        p.replaceChild(whiteSpaces[i], node)
                        break
                    } catch (e: AssertionError) {
                    }
                }
            }
            return false
        } else return true
    }

    override fun replaceNodeIfPossible(node: ASTNode, replacement: ASTNode): Boolean {
        if (node.text.isEmpty() || node == replacement) return checkTest()

        //If we trying to replace parent node to it child
        if (node.getAllChildrenNodes().contains(replacement)) {
            val backup = node.copyElement()
            node.replaceThis(replacement)
            if (!checkTest()) {
                log.debug("REPLACING BACK")
                replacement.replaceThis(backup)
            } else {
                log.debug("SUCCESSFUL DELETING")
                return true
            }
        }

        //Else
        for (p in node.getAllParentsWithoutNode()) {
            try {
                val oldText = curFile.text
                if ((node as TreeElement).treeParent !== p) continue
                p.replaceChild(node, replacement)
                if (oldText == curFile.text)
                    continue
                if (!checkTest()) {
                    log.debug("REPLACING BACK")
                    p.replaceChild(replacement, node)
                    return false
                } else {
                    log.debug("SUCCESSFUL DELETING")
                    return true
                }
            } catch (e: AssertionError) {
                log.debug("Exception while deleting ${node.text} from $p")
            }
        }
        return false
    }

    fun isAlreadyCheckedOrWrong(projectHash: Int, bbfFile: BBFFile): Pair<Boolean, Boolean> {
        if (alreadyChecked.containsKey(projectHash)) {
            log.debug("ALREADY CHECKED!!!")
            return true to alreadyChecked[projectHash]!!
        }
        if (bugType != BugType.FRONTEND && bbfFile.isPsiWrong()) {
            alreadyChecked[projectHash] = false
            return true to false
        }
        return false to false
    }

    fun isAlreadyCheckedOrWrong(): Pair<Boolean, Boolean> = isAlreadyCheckedOrWrong(projectHash, curFile)

    override fun checkTest(): Boolean {
        val firstCheck = isAlreadyCheckedOrWrong()
        if (firstCheck.first) return firstCheck.second
        val isBug = compiler!!.isCompilerBug(project)
        alreadyChecked[project.toString().trim().hashCode()] = isBug
        return isBug
    }

    @Deprecated("")
    override fun checkTest(text: String): Boolean {
        val hash = text.trim().hashCode()
        if (alreadyChecked.containsKey(hash)) {
            log.debug("ALREADY CHECKED!!!")
            return alreadyChecked[hash]!!
        }
        if (bugType != BugType.FRONTEND) {
            val psi = Factory.psiFactory.createFile(text)
            if (psi.containsChildOfType<PsiErrorElement>()) {
                alreadyChecked[hash] = false
                return false
            }
        }
        val isBug = compiler!!.isCompilerBug(text)
        alreadyChecked[hash] = isBug
        return isBug
    }

    @Deprecated("")
    override fun replaceNodeIfPossible(tree: FileASTNode, node: ASTNode, replacement: ASTNode): Boolean {
        if (node.text.isEmpty() || node == replacement) return checkTest(tree.text)

        //If we trying to replace parent node to it child
        if (node.getAllChildrenNodes().contains(replacement)) {
            val backup = node.copyElement()
            node.replaceThis(replacement)
            if (!checkTest(tree.text)) {
                log.debug("REPLACING BACK")
                replacement.replaceThis(backup)
            } else {
                log.debug("SUCCESSFUL DELETING")
                return true
            }
        }

        //Else
        for (p in node.getAllParentsWithoutNode()) {
            try {
                val oldText = tree.text
                if ((node as TreeElement).treeParent !== p) continue
                p.replaceChild(node, replacement)
                if (oldText == tree.text)
                    continue
                if (!checkTest(tree.text)) {
                    log.debug("REPLACING BACK")
                    p.replaceChild(replacement, node)
                    return false
                } else {
                    log.debug("SUCCESSFUL DELETING")
                    return true
                }
            } catch (e: AssertionError) {
                log.debug("Exception while deleting ${node.text} from $p")
            }
        }
        return false
    }

    @Deprecated("")
    override fun removeNodeIfPossible(tree: FileASTNode, node: ASTNode): Boolean {
        val tmp = Factory.psiFactory.createWhiteSpace("\n")
        return replaceNodeIfPossible(tree, node, tmp.node)
    }

    override fun getErrorMessage(): String = compiler!!.getErrorMessage(project)
    override fun getErrorMessageWithLocation() = compiler!!.getErrorMessageWithLocation(project)

    override val alreadyChecked: HashMap<Int, Boolean> = HashMap()
    open val log = Logger.getLogger("reducerLogger")
    val projectHash: Int
        get() = project.toString().trim().hashCode()
}