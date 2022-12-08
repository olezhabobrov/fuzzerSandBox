package com.stepanov.bbf.bugfinder.mutator.projectTransformations

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.util.replaceThis
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import java.io.File
import kotlin.system.exitProcess

object ProjectPreprocessor {

    private fun getFunTypeAsStr(ctx: BindingContext, func: KtNamedFunction): String? {
        func.typeReference?.let { return it.text }
        func.bodyExpression?.let { return it.getType(ctx)?.toString() }
        return null
    }

    fun preprocess(proj: Project, checker: MutationChecker?) {
        for (file in proj.files) {
            val functions = file.psiFile.getAllPSIChildrenOfType<KtNamedFunction>()
            val ctx = PSICreator.analyze(file.psiFile) ?: continue //file.ctx ?: continue
            for (f in functions) {
                val funType = getFunTypeAsStr(ctx, f) ?: continue
                try {
                    val typeRef = Factory.psiFactory.createTypeIfPossible(funType) ?: continue
                    val todoExpr = Factory.psiFactory.createExpression("TODO()")
                    if (f.bodyBlockExpression != null) {
                        if (f.bodyBlockExpression!!.getAllPSIChildrenOfType<KtReturnExpression>().isNotEmpty() ||
                            f.typeReference != null)
                            f.typeReference = typeRef
                        else {
                            f.typeReference = Factory.psiFactory.createType("Unit")
                        }
                        f.bodyBlockExpression!!.replaceThis(todoExpr)
                        f.addBefore(Factory.psiFactory.createEQ(), todoExpr)
                    } else {
                        val oldTypeRef = f.typeReference
                        if (f.bodyExpression == null) continue
                        if (checker != null) {
                            f.typeReference = typeRef
                            if (!checker.replaceNodeIfPossible(f.bodyExpression!!.node, todoExpr.node)) {
                                f.typeReference = oldTypeRef
                            }
                        } else {
                            f.bodyExpression?.replaceThis(todoExpr)
                            f.typeReference = typeRef
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }


    fun preDir() {
        var fl = false
        for (f in File(CompilerArgs.baseDir).listFiles().filter { it.absolutePath.endsWith("kt") }
            .sortedBy { it.name }) {
            if (f.name == "commonSupertypeContravariant2.kt") fl = true
            if (!fl) continue
            val comp = JVMCompiler("")
            println(f.name)
            val proj = Project.createFromCode(f.readText())
            if (proj.files.size != 1) continue
            val c1 = comp.checkCompiling(proj)
            println("c1 = $c1")
            preprocess(proj, null)
            val c2 = comp.checkCompiling(proj)
            println("c2 = $c2")
            if (!c2 && c1) {
                val proj1 = Project.createFromCode(f.readText())
                val checker = MutationChecker(JVMCompiler(), proj1)
                preprocess(proj1, checker)
                if (!comp.checkCompiling(proj1)) {
                    println(proj1)
                    exitProcess(0)
                }
                File("/home/stepanov/Kotlin/bbfgradle/tmp/abiTests/${f.name}").writeText(proj1.toString())
            } else {
                File("/home/stepanov/Kotlin/bbfgradle/tmp/abiTests/${f.name}").writeText(proj.toString())
            }

        }
    }

}