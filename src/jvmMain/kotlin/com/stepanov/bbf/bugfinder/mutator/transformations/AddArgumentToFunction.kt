package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.calls.callUtil.getFunctionResolvedCallWithAssert
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import kotlin.random.Random
import kotlin.system.exitProcess

//TODO add field to class
class AddArgumentToFunction : Transformation() {

    override fun transform() {
        repeat(MAGIC_CONST) {
            try {
                val ktFile = file as? KtFile ?: return
                val fileBackup = ktFile.copy() as KtFile
                val ctx = PSICreator.analyze(ktFile) ?: return
                RandomTypeGenerator.setFileAndContext(ktFile, ctx)
                val randomFunc = ktFile.getAllPSIChildrenOfType<KtNamedFunction>().randomOrNull() ?: return
                val availableTypeParams = getTypeParamsFromCurrentScope(randomFunc).toList()
                var newType = RandomTypeGenerator.generateRandomTypeWithCtx() ?: return
                if (availableTypeParams.isNotEmpty()) {
                    val rt = newType.replaceTypeOrRandomSubtypeOnTypeParam(availableTypeParams)
                    newType = RandomTypeGenerator.generateType(rt) ?: return@repeat
                }
                //Add default value
                val defaultValue =
                    if (Random.getTrue(20)) {
                        RandomInstancesGenerator(ktFile, ctx).generateValueOfType(newType)
                    } else ""
                val defaultValueAsString = if (defaultValue.isEmpty()) "" else " = $defaultValue"
                val callers = ktFile.getAllPSIChildrenOfType<KtCallExpression>()
                    .filter { it.getFunctionResolvedCallWithAssert(ctx).resultingDescriptor.findPsi() == randomFunc }
                val generatedParam =
                    Factory.psiFactory.createParameter("${Random.getRandomVariableName(4)}: $newType $defaultValueAsString")
                randomFunc.valueParameterList!!.addParameter(generatedParam)
                callers.forEach { call ->
                    if (defaultValueAsString.isNotEmpty() && Random.getTrue(10)) return@forEach
                    val resolvedTypeParams =
                        call.getResolvedCall(ctx)?.typeArguments?.map { it.key.name.asString() to it.value.toString() }
                            ?.toMap()
                            ?: return@forEach
                    val resolvedNewType =
                        "$newType"
                            .splitWithoutRemoving(Regex("[<>]"))
                            .map { resolvedTypeParams[it] ?: it }
                            .joinToString(separator = "")
                    val resolvedNewTypeAsKotlinType =
                        RandomTypeGenerator.generateType(resolvedNewType) ?: return@forEach
                    var generatedValue =
                        RandomInstancesGenerator(ktFile, ctx).generateValueOfType(resolvedNewTypeAsKotlinType)
                    if (generatedValue.isNotEmpty()) {
                        if (resolvedNewType.trim() == "$newType".trim() && generatedValue.contains('<')) {
                            generatedValue =
                                generatedValue.substringBefore('<') + generatedValue.substringAfterLast('>')
                        }
                        val genValuePsi = Factory.psiFactory.createArgument(generatedValue)
                        if (call.valueArgumentList == null) {
                            val newValueArgumentList = call.getOrCreateValueArgumentList()
                            val lambda = call.valueArguments.firstOrNull()?.copy() as? KtValueArgument
                            call.valueArguments.forEach { it.delete() }
                            val valArgsWithLambda = (lambda?.let { it.text + ", " } ?: "") + genValuePsi.text
                            val valArgsWithLambdaPsi = Factory.psiFactory.createCallArguments("(${valArgsWithLambda})")
                            newValueArgumentList.replaceThis(valArgsWithLambdaPsi)
                        } else {
                            call.valueArgumentList!!.addArgument(genValuePsi)
                        }
                    }
                }
                if (!checker.checkCompiling()) {
                    checker.curFile.changePsiFile(fileBackup, genCtx = false)
                }
            } catch (e: Exception) {
            } catch (e: Error) {
            }
        }
    }

    private fun getTypeParamsFromCurrentScope(psiElement: PsiElement) =
        psiElement.parentsWithSelf
            .filter { it is KtNamedFunction || it is KtClassOrObject }
            .flatMap { (it as KtTypeParameterListOwner).typeParameters }
            .map { it.name ?: "" }
            .filter { it.isNotEmpty() }
            .toSet()


    private fun DeclarationDescriptor.findPsi(): PsiElement? {
        val psi = (this as? DeclarationDescriptorWithSource)?.source?.getPsi()
        return if (psi == null && this is CallableMemberDescriptor && kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
            overriddenDescriptors.mapNotNull { it.findPsi() }.firstOrNull()
        } else {
            psi
        }
    }

    private fun SourceElement.getPsi(): PsiElement? = (this as? PsiSourceElement)?.psi

    private val MAGIC_CONST = file.getAllPSIChildrenOfType<KtNamedFunction>().size / 2
}