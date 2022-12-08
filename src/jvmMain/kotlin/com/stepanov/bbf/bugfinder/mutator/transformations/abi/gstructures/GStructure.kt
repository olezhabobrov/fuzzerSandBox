package com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures

abstract class GStructure {
    abstract var modifiers: MutableList<String>

    fun isPrivate() = modifiers.contains("private")
    fun isAbstract() = modifiers.contains("abstract")
    fun isInline() = modifiers.contains("inline")

    fun getVisibility() =
        when {
            modifiers.contains("public") -> "public"
            modifiers.contains("private") -> "private"
            modifiers.contains("internal") -> "internal"
            modifiers.contains("protected") -> "protected"
            else -> "public"
        }
}