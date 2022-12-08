package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.tryToCreateExpression
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.util.ScopeCalculator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.replace
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import kotlin.random.Random
import kotlin.system.exitProcess

class AddLoop : Transformation() {

    override fun transform() {
        repeat(RANDOM_CONST) {
            val backup = file.copy() as PsiFile
            try {
                addRandomLoops()
            } catch (e: Exception) {
                checker.curFile.changePsiFile(backup, false)
            }
        }
    }

    private fun addRandomLoops() {
        val beginOfLoop = Random.nextInt(file.text.split("\n").size - 2)
        val endOfLoop = Random.nextInt(beginOfLoop + 1, file.text.split("\n").size - 1)
        val fileBackup = file.copy() as PsiFile
        val nodesBetweenWhS = file.getNodesBetweenWhitespaces(beginOfLoop, endOfLoop)
        if (nodesBetweenWhS.isEmpty() || nodesBetweenWhS.all { it is PsiWhiteSpace }) return
        val randomLoop = generateRandomLoop(beginOfLoop to endOfLoop) ?: return
        nodesBetweenWhS.first { it !is PsiWhiteSpace }.replaceThis(randomLoop)
        val whiteSpaces = nodesBetweenWhS.takeWhile { it is PsiWhiteSpace }
        nodesBetweenWhS
            .filter { it.parent !in nodesBetweenWhS && it !in whiteSpaces }
            .map { it.delete() }
        if (!checker.checkCompiling()) {
            checker.curFile.changePsiFile(fileBackup, false)
        }
    }

    private fun generateRandomLoop(placeToInsert: Pair<Int, Int>): KtExpression? {
        val beginningNode =
            file.getNodesBetweenWhitespaces(placeToInsert.first, placeToInsert.first)
                .firstOrNull { it is KtExpression } ?: return null
        val ctx = PSICreator.analyze(file, checker.project) ?: return null
        val rig = RandomInstancesGenerator(file as KtFile, ctx)
        RandomTypeGenerator.setFileAndContext(file as KtFile, ctx)
        val scope =
            ScopeCalculator(file as KtFile, project)
                .calcScope(beginningNode)
                .map { it.psiElement to it.type }
        //val scope = (file as KtFile).getAvailableValuesToInsertIn(beginningNode, ctx).filter { it.second != null }
        val nodesBetweenWhS = file.getNodesBetweenWhitespaces(placeToInsert.first, placeToInsert.second)
        val body = nodesBetweenWhS.filter { it.parent !in nodesBetweenWhS }.joinToString("") { it.text }
        return if (Random.getTrue(75)) {
            generateForExpression(scope, rig, body)
        } else {
            generateWhileExpression(scope, rig, body)
        }
    }

    private fun generateForExpression(
        scope: List<Pair<PsiElement, KotlinType?>>,
        rig: RandomInstancesGenerator,
        body: String
    ): KtExpression? {
        val containerFromScopeToIterate =
            scope.filter { it.second!!.isIterable() || it.second!!.getNameWithoutError().contains("Range") }
        val variablesFromScopeToIterate =
            scope.filter { it.second!!.getNameWithoutError() in typesToIterate }
        val loopRange =
            if (containerFromScopeToIterate.isNotEmpty() && Random.getTrue(50)) {
                containerFromScopeToIterate.random().first
            } else if (variablesFromScopeToIterate.isNotEmpty() && Random.getTrue(20)) {
                val randomVar = variablesFromScopeToIterate.random()
                val left = randomVar.first
                val rightFromScope =
                    variablesFromScopeToIterate
                        .find { it.second!!.name == randomVar.second!!.name && it.first.text != randomVar.first.text }
                val right =
                    if (rightFromScope != null && Random.getTrue(50))
                        rightFromScope.first
                    else rig.generateValueOfTypeAsExpression(randomVar.second!!)!!
                Factory.psiFactory.createExpression("${left.text}..${right.text}")
            } else {
                var resExpr: KtExpression? = null
                for (i in 0 until 10) {
                    val randomClassToIterate = getRandomClassToIterate()
                    val instance = rig.generateValueOfType(randomClassToIterate)
                    resExpr = Factory.psiFactory.tryToCreateExpression(instance)
                    if (resExpr != null) break
                }
                resExpr
            }
        if (loopRange == null) return null
        val label =
            if (Random.getTrue(25)) "${Random.getRandomVariableName(1)}@"
            else ""
        val loopParameter = Random.getRandomVariableName(1)
        val forExpression = "$label for ($loopParameter in ${loopRange.text}) { $body\n}"
        return try {
            Factory.psiFactory.createExpression(forExpression)
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
    }

    private fun generateWhileExpression(
        scope: List<Pair<PsiElement, KotlinType?>>,
        rig: RandomInstancesGenerator,
        body: String
    ): KtExpression? {
        val label =
            if (Random.getTrue(50)) "${Random.getRandomVariableName(1)}@"
            else ""
        val randomExpression = scope.filter { it.second?.isBoolean() == true }.randomOrNull()
        val whileCondition = randomExpression?.first?.text
//            if (randomExpression != null && Random.getTrue(80)) {
//                val newExpression = rig.generateValueOfTypeAsExpression(randomExpression.second!!)
//                if (newExpression == null) {
//                    "${randomExpression.first.text} != ${randomExpression.first.text}"
//                } else {
//                    "${randomExpression.first.text} != ${newExpression.text}"
//                }
//            } else {
//                "true == true"
//            }
        return try {
            val strWhile =
                if (Random.getTrue(70)) {
                    "$label while ($whileCondition) {\n$body\n}"
                } else {
                    "$label do {\n$body\n} while($whileCondition)"
                }
            Factory.psiFactory.tryToCreateExpression(strWhile)
        } catch (e: Exception) {
            null
        }
    }

    private fun getRandomClassToIterate(): KotlinType {
        val randomClass = randomClassesToIterate.random()
        //Substitute type parameters
        val realTypeParams = randomClass.typeConstructor.parameters.map {
            RandomTypeGenerator.generateRandomTypeWithCtx(it.upperBounds.firstOrNull()) ?: DefaultKotlinTypes.intType
        }
        return randomClass.defaultType.replace(realTypeParams.map { it.asTypeProjection() })
    }

    private val randomClassesToIterate =
        StdLibraryGenerator.klasses
            .filter {
                it.getAllSuperClassifiersWithoutAnyAndItself()
                    .map { it.name.asString() }
                    .let { it.contains("Iterable") || it.contains("ClosedRange") }
            }
            .filterDuplicatesBy { it.name }

    private val typesToIterate = listOf("Byte", "UByte", "Char", "Int", "UInt", "Long", "ULong", "Short", "UShort")
    private val RANDOM_CONST = Random.nextInt(25, 50)
}