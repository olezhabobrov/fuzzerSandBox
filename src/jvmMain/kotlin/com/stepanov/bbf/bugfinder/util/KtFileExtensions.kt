package com.stepanov.bbf.bugfinder.util

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

fun KtFile.getClassWithName(name: String): KtClassOrObject? =
    this.getAllPSIChildrenOfType<KtClassOrObject>().find {
        val className =
            it.parentsWithSelf.filterIsInstance<KtClassOrObject>()
                .toList()
                .reversed()
                .joinToString(".") { it.name ?: "" }
        className == name
    }