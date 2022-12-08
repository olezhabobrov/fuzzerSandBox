package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.util.getAllPSIDFSChildrenOfType
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.util.replaceThis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.isFunctionalExpression

class AddReificationToTypeParam: Transformation() {
    override fun transform() {
        val funcsWithTypeParams =
            file.getAllPSIDFSChildrenOfType<KtNamedFunction>()
                .filter { it.typeParameters.isNotEmpty() }
                .reversed()
        for (f in funcsWithTypeParams) {
            val backup = f.copy() as KtNamedFunction
            if (!f.hasModifier(KtTokens.INLINE_KEYWORD))
                f.addModifier(KtTokens.INLINE_KEYWORD)
            for (vp in f.valueParameters) {
                if (vp.typeReference?.children?.firstOrNull() is KtFunctionType) {
                    val newVp = Factory.psiFactory.createParameter("crossinline ${vp.text}")
                    vp.replaceThis(newVp)
                }
            }
            for (tp in f.typeParameters) {
                val newTp = Factory.psiFactory.createTypeParameter("reified ${tp.text}")
                tp.replaceThis(newTp)
            }
            if (!checker.checkCompiling()) {
                f.replaceThis(backup)
            }
        }
    }
}