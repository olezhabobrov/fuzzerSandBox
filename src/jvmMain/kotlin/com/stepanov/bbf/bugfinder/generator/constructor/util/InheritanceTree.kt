package com.stepanov.bbf.bugfinder.generator.constructor.util

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.KotlinType
import java.io.File


data class InheritanceTreeNode(
    val type: String,
    val supertypes: List<String>,
    val childrenTypes: List<String>
)

class InheritanceTree(val file: KtFile, val root: InheritanceTreeNode) {
}

