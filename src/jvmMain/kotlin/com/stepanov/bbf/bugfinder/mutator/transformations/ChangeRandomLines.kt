package com.stepanov.bbf.bugfinder.mutator.transformations


import java.util.*
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory

class ChangeRandomLines : Transformation() {

    override fun transform() {
        val text = file.text.lines().toMutableList()
        for (i in 0..Random().nextInt(shuffleConst)) {
            val numLine = Random().nextInt(text.size)
            val insLine = Random().nextInt(text.size)
            Collections.swap(text, numLine, insLine)
            if (!checker.checkTextCompiling(getText(text))) {
                Collections.swap(text, numLine, insLine)
            }
        }
        checker.curFile.changePsiFile(psiFactory.createFile(getText(text)))
        //file = psiFactory.createFile(getText(text))
    }

    private fun getText(text: MutableList<String>) = text.joinToString(separator = "\n")

    private val shuffleConst = file.text.lines().size * 4
}