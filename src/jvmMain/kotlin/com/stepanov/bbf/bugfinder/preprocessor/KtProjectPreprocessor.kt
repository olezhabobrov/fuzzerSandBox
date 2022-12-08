package com.stepanov.bbf.bugfinder.preprocessor

import com.stepanov.bbf.bugfinder.executor.checkers.CompilationChecker
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.apache.log4j.Logger
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.psi.psiUtil.parents
import kotlin.random.Random

object KtProjectPreprocessor {

    //Returns null if we cant compile
    fun preprocess(project: Project, checker: CompilationChecker): List<KtFile>? = TODO()
//    {
//        var psiFiles = buildPsiForProject(project)
//        renameBoxFun(psiFiles)
//        //Rename redeclarations
//        val errorMsg = checker.compileAndGetMessage(Project(psiFiles))
//        if (errorMsg.contains("Redeclaration:")) {
//            log.debug("RENAMING")
//            val renameNameReferences =
//                errorMsg
//                    .split("\n")
//                    .filter { it.startsWith("Redeclaration: ") }
//                    .map { it.split("Redeclaration: ").last() }
//                    .toSet()
//            //If we have one file, which should be split to compile, we need
//            //gather it back to one file before renaming
//            val notSplittedProj = project.map { PSICreator.getPSIForText(it, false) }
//            Renamer().rename(notSplittedProj, renameNameReferences)
//            psiFiles = buildPsiForProject(notSplittedProj.map { it.text })
//            renameBoxFun(psiFiles)
//        } else if (errorMsg.trim().isNotEmpty()) {
//            log.debug(errorMsg)
//            return null
//        }
//        val shuffledPsiFiles = psiFiles.shuffle()
//        val factory = KtPsiFactory(shuffledPsiFiles.first().project)
////        //You didn't see it...
////        var counters = mutableListOf(0, 0, 0, 0, 0)
////        files.forEach { Anonymizer.anonymizeAnonimized(it, counters, true) }
////        counters = mutableListOf(0, 0, 0, 0, 0)
////        files.forEach { Anonymizer.anonymizeAnonimized(it, counters, false) }
//
//        if (Random.nextBoolean()) {
//            val newPackageDirectives = createRandomPackageDirectives(shuffledPsiFiles.size, factory)
//            shuffledPsiFiles.forEachIndexed { index, file ->
//                file.packageDirective?.replaceThis(newPackageDirectives[index])
//                file.packageDirective?.add(factory.createWhiteSpace("\n\n\n"))
//            }
//        }
//        //Shuffle box funcs
//        shuffledPsiFiles.boxShift(factory)
//        val imports = shuffledPsiFiles.map { ImportsGetter().createImportFromPackageDirective(it) }
//
//        for (i in shuffledPsiFiles.indices) {
//            shuffledPsiFiles[i].importList?.add(factory.createWhiteSpace("\n"))
//            imports.getAllWithout(i).filterNotNull().forEach { shuffledPsiFiles[i].addImport(it) }
//        }
//        if (!checker.isCompilationSuccessful(Project(shuffledPsiFiles))) {
//            log.debug("Could not compile after import transferring")
//            log.debug(shuffledPsiFiles.map { it.text })
//            return null
//        }
//        val splitFiles = ClassSplitter(shuffledPsiFiles, checker)
//            .split()
//            .filterNot {
//                it.text.trim().isEmpty() || it.allChildren.toList()
//                    .all { it is PsiComment || it is KtImportList || it is KtPackageDirective || it is PsiWhiteSpace }
//            }
//        if (!checker.isCompilationSuccessful(Project(splitFiles))) {
//            log.debug("Cant compile after splitting")
//            return null
//        }
//        return splitFiles
//    }

    private fun buildPsiForProject(project: List<String>): List<KtFile> = TODO()
//    {
//        val splitProject = Project(project.map { Project(it).split() }.flatMap { it.texts })
//        return splitProject.texts.map { PSICreator.getPSIForText(it, false) }
//    }

    private fun renameBoxFun(psiFiles: List<KtFile>) {
        //Rename of box() fun
        psiFiles.forEachIndexed { index, file ->
            file.getAllPSIChildrenOfType<KtNamedFunction>()
                .find { it.name == "box" }
                ?.setName("box$index")
        }
    }

    private fun List<KtFile>.shuffle(): List<KtFile> {
        val factory = KtPsiFactory(this.first().project)
        val res = mutableListOf<KtFile>()
        this.forEach { res.add(it) }
        val topLvlEls =
            this.flatMap {
                it.getAllChildren().filter {
                    it.isTopLevelKtOrJavaMember() && it is KtModifierListOwner && !it.text.contains("fun box")
                }
            }
        // Add public modifiers and shuffle
        val publicModifier = KtTokens.MODIFIER_KEYWORDS_ARRAY.find { it.value == "public" }!!
        // Randomly grouping
        for (el in topLvlEls) {
            val oldFile = el.parents.first { it is KtFile } as KtFile
            if (Random.getTrue(25)) {
                (el as KtModifierListOwner).addModifier(publicModifier)
                val newFile =
                    factory.createFile("${oldFile.packageDirective?.text}\n${oldFile.importList?.text}\n${el.text}")
                //Copy imports
                res.add(newFile)
                el.replaceThis(factory.createWhiteSpace("\n"))
            } else if (Random.getTrue(25)) {
                //Put in random file
                (el as KtModifierListOwner).addModifier(publicModifier)
                val randomFile = res.random()
                if (randomFile.getAllChildren().contains(el)) continue
                //Add imports
                oldFile.importDirectives.forEach { randomFile.addImport(it) }
                randomFile.add(factory.createWhiteSpace("\n\n"))
                randomFile.add(el)
                el.replaceThis(factory.createWhiteSpace("\n"))
            }
        }
        return res
    }

    private fun KtFile.addImport(import: KtImportDirective) {
        if (this.importDirectives.any { it.text == import.text }) return
        this.importList?.add(import)
        this.importList?.add(KtPsiFactory(this.project).createWhiteSpace("\n"))
    }

    private fun List<KtFile>.boxShift(psiFactory: KtPsiFactory) {
        val boxFuncs = this.map { file ->
            file.getAllPSIChildrenOfType<KtNamedFunction>().find { it.name?.contains("box") ?: false } ?: return
        }
        val copyOfBox = boxFuncs.map { it.copy() as KtNamedFunction }.toMutableList()
        val lastBox = copyOfBox.last().copy() as KtNamedFunction
        copyOfBox.add(0, lastBox)
        copyOfBox.removeAt(copyOfBox.size - 1)
        boxFuncs.forEachIndexed { index, f -> f.replaceThis(copyOfBox[index]) }
    }

    fun createRandomPackageDirectives(num: Int): List<KtPackageDirective> {
        val factory = Factory.psiFactory
        val result = mutableListOf<KtPackageDirective>()
        result.add(factory.createPackageDirective(FqName("a")))
        for (i in 1 until num) {
            if (Random.nextBoolean()) {
                //Not nested
                result.add(factory.createPackageDirective(FqName("${'a' + i}")))
            } else {
                //Nested
                val randomDirective = result.random()
                result.add(factory.createPackageDirective(FqName("${randomDirective.fqName}.${'a' + i}")))
            }
        }
        return result
    }

    private val log = Logger.getLogger("bugFinderLogger")
}