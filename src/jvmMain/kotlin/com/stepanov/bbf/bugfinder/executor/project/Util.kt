package com.stepanov.bbf.bugfinder.executor.project

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory

internal fun getCommentSection(text: String) =
    text.lineSequence()
        .takeWhile { it.startsWith("//") || it.trim().isEmpty() }
        .joinToString("\n")