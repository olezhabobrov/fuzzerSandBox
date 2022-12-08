package com.stepanov.bbf.bugfinder.mutator.transformations

import java.util.*
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory

class RemoveRandomLines: Transformation()  {

    override fun transform() {
        val text = file.text.lines().toMutableList()
        for (i in 0..Random().nextInt(removeConst)) {
            val numLine = Random().nextInt(text.size)
            val old = text[numLine]
            text[numLine] = ""
            if (!checker.checkTextCompiling(getText(text))) {
                text[numLine] = old
            }
        }
        checker.curFile.changePsiFile(psiFactory.createFile(getText(text)))
    }

    private fun getText(text: MutableList<String>) = text.joinToString(separator = "\n")

    private val removeConst = file.text.lines().size * 2
}