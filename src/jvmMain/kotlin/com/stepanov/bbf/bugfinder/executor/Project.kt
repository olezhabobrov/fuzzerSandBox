//package com.stepanov.bbf.bugfinder.executor
//
//import com.intellij.psi.PsiFile
//import org.jetbrains.kotlin.psi.KtFile
//import org.jetbrains.kotlin.psi.KtPsiFactory
//
//enum class LANGUAGE {
//    KOTLIN,
//    KJAVA,
//    JAVA
//}
//
////Just string representation of ktFiles
//class Project(texts: List<String>?, files: List<PsiFile>? = null, val language: LANGUAGE = LANGUAGE.KOTLIN) {
//
//    val texts: List<String> = texts?.let { it } ?: files!!.map { it.text }
//
//    constructor(text: String) : this(listOf(text))
//    constructor(text: String, files: List<PsiFile>? = null, language: LANGUAGE = LANGUAGE.KOTLIN) : this(
//        listOf(text),
//        files,
//        language
//    )
//
//    constructor(files: List<PsiFile>, language: LANGUAGE = LANGUAGE.KOTLIN) : this(null, files, language)
//    constructor(file: PsiFile, language: LANGUAGE = LANGUAGE.KOTLIN) : this(listOf(file), language)
//
//    operator fun plus(other: Project?) =
//        other?.let { Project(texts + other.texts, null, this.language) } ?: Project(texts)
//
//    fun getCommonText(commonPath: String) =
//        texts.mapIndexed { index, s -> "//File: ${commonPath.split(" ")[index]}\n$s" }.joinToString("\n")
//
//    fun getCommonTextWithDefaultPath() =
//        if (texts.any { it.contains(Regex("""//\s*((FILE)|(File))""")) }) texts.joinToString("\n")
//        else
//            when (language) {
//                LANGUAGE.KJAVA -> texts.joinToString("\n")
//                else -> texts
//                    .mapIndexed { index, s -> "//File: ${CompilerArgs.pathToTmpFile.substringBefore(".kt")}$index.kt\n$s" }
//                    .joinToString("\n")
//            }
//
//
//    fun getKtFiles(psiFactory: KtPsiFactory): List<KtFile> = texts.map { psiFactory.createFile(it) }
//
//    override fun equals(other: Any?): Boolean =
//        other is Project && other.texts == this.texts
//
//    override fun hashCode(): Int {
//        var result = language.hashCode()
//        result = 31 * result + texts.hashCode()
//        return result
//    }
//
//    override fun toString(): String = texts.joinToString("\n\n")
//}