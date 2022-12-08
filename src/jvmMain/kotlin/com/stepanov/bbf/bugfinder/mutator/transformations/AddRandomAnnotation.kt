package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.addImport
import com.stepanov.bbf.bugfinder.util.addToTheTop
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfTwoTypes
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.types.KotlinType
import kotlin.random.Random

class AddRandomAnnotation : Transformation() {

    private var randomInstancesGenerator: RandomInstancesGenerator? = null

    private val annClassesFromLib =
        StdLibraryGenerator.klasses
            .filter {
                it.kind == ClassKind.ANNOTATION_CLASS
                        && it.visibility.isPublicAPI
                        && !it.findPackage().toString().contains("js")
            }

    private val RANDOM_CONST = 50

    private fun insert(t: PsiElement, type: KotlinType?, kl: KtClass?) {
        //println("trying to insert ${type ?: kl!!.name}")
        val instance =
            type?.let { randomInstancesGenerator!!.generateValueOfType(type) }
                ?: randomInstancesGenerator!!.generateRandomInstanceOfClass(kl!!)?.first?.text
        //println("instance = $instance")
        if (instance == null || instance.isEmpty()) return
        val newNode: PsiElement
        if (t == file) {
            val psiInstance = Factory.psiFactory.createFileAnnotation(instance)
            newNode = file.addToTheTop(psiInstance)
        } else {
            val psiInstance = Factory.psiFactory.createAnnotationEntry("@$instance")
            val psiWhiteSpace = Factory.psiFactory.createWhiteSpace("\n")
            newNode = t.addAfter(psiInstance, null)
            newNode.addBefore(psiWhiteSpace, null)
        }
        if (!checker.checkCompiling()) newNode.delete()
    }

    fun tryToInsertAnn() {
        val annClassFromFile = file.getAllPSIChildrenOfType<KtClass>().filter { it.isAnnotation() }.randomOrNull()
        if (annClassFromFile != null && Random.nextBoolean()) {
            val target =
                annClassFromFile.annotationEntries
                    .find { it.shortName?.asString() == "Target" }
                    ?.valueArguments
                    ?.mapNotNull {
                        it.asElement().text?.substringAfter('.')
                    } ?: possibleTargets
            val potTargets = target
                .flatMap { getTargetsByTarget(it) }
                .toSet()
                .filter { it != annClassFromFile && !it.parents.contains(annClassFromFile) }
            //if (potTargets.size > 10) potTargets = potTargets.filter { Random.nextBoolean() }
            insert(potTargets.random(), null, annClassFromFile)
            return
        }

        val annClassFromLib = annClassesFromLib.random()
        val classId = annClassFromLib.classId?.packageFqName?.asString()
        if (classId != null && classId != "kotlin") {
            (file as KtFile).addImport(annClassFromLib.classId!!.asString().replace('/', '.').substringBeforeLast('.'), true)
        }
        val targetValueArgs =
            annClassFromLib.annotations.findAnnotation(FqName("kotlin.annotation.Target"))?.allValueArguments
        val target = targetValueArgs?.values?.firstOrNull()?.value as List<*>? ?: possibleTargets
        val potTargets = target.flatMap { getTargetsByTarget(it.toString().substringAfter('.')) }
        val randomTarget = potTargets.randomOrNull() ?: return
        insert(randomTarget, annClassFromLib.defaultType, null)
        return
    }

    override fun transform() {
        val ctx = PSICreator.analyze(file, project) ?: return
        randomInstancesGenerator = RandomInstancesGenerator(file as KtFile, ctx)
        repeat(RANDOM_CONST) { tryToInsertAnn() }
    }


    private fun getTargetsByTarget(target: String): List<PsiElement> =
        when (target) {
            "CLASS" -> file.getAllPSIChildrenOfType<KtClass>()
            "ANNOTATION_CLASS" -> file.getAllPSIChildrenOfType<KtClass>().filter { it.isAnnotation() }
            "TYPE_PARAMETER" -> file.getAllPSIChildrenOfType<KtTypeParameter>()
            "PROPERTY" -> file.getAllPSIChildrenOfType<KtProperty>()
            "FIELD" -> file.getAllPSIChildrenOfType<KtClass>().flatMap { it.getProperties() }
            "LOCAL_VARIABLE" -> file.getAllPSIChildrenOfType<KtNamedFunction>()
                .flatMap { it.getAllPSIChildrenOfType<KtProperty>() }
            "VALUE_PARAMETER" -> file.getAllPSIChildrenOfType<KtValueArgument>()
            "CONSTRUCTOR" -> file.getAllPSIChildrenOfTwoTypes<KtPrimaryConstructor, KtSecondaryConstructor>()
            "FUNCTION" -> file.getAllPSIChildrenOfType<KtNamedFunction>().filter { it.isTopLevel }
            "PROPERTY_GETTER" -> file.getAllPSIChildrenOfType<KtProperty>().mapNotNull { it.getter }
            "PROPERTY_SETTER" -> file.getAllPSIChildrenOfType<KtProperty>().mapNotNull { it.setter }
            "TYPE" -> file.getAllPSIChildrenOfType<KtTypeReference>()
            "EXPRESSION" -> file.getAllPSIChildrenOfType<KtExpression>()
            "FILE" -> listOf(file)
            "TYPEALIAS" -> file.getAllPSIChildrenOfType<KtTypeAlias>()
            else -> listOf()
        }

    private val possibleTargets = listOf(
        "CLASS", "ANNOTATION_CLASS", "TYPE_PARAMETER", "PROPERTY",
        "FIELD", "LOCAL_VARIABLE", "VALUE_PARAMETER", "CONSTRUCTOR", "FUNCTION",
        "PROPERTY_GETTER", "PROPERTY_SETTER", "TYPE", "EXPRESSION", "FILE", "TYPEALIAS"
    )

}