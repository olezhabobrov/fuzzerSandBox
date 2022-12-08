package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.UsagesSamplesGenerator
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.util.replaceThis
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.typeUtil.isInt

class MutationExample : Transformation() {
    private val currentFile = file as KtFile
    private val bindingContext = PSICreator.analyze(file, project)
    private var rig: RandomInstancesGenerator? = null

    override fun transform() {
        bindingContext ?: return
        rig = RandomInstancesGenerator(currentFile, bindingContext)
        replaceStringConstant()
        replaceIntConstantWithClassValue()
    }

    private fun replaceIntConstantWithClassValue() {
        val intConstant =
            currentFile.getAllPSIChildrenOfType<KtConstantExpression>()
                .map { it to it.getType(bindingContext!!) }
                .first { it.second != null && it.second!!.isInt() }
                .first
        val constantCopy = intConstant.copy()
        val userClass = currentFile.getAllPSIChildrenOfType<KtClass>().first()
        val userClassDescriptor =
            userClass.getDeclarationDescriptorIncludingConstructors(bindingContext!!) as ClassDescriptor
        val usageOfIntType =
            UsagesSamplesGenerator.generateClassUsages(currentFile, bindingContext, userClassDescriptor)
                .first { it.second?.isInt() == true }
                .first
        intConstant.replaceThis(usageOfIntType)
        if (!checker.checkCompiling()) {
            usageOfIntType.replaceThis(constantCopy)
        }
    }

    private fun replaceStringConstant() {
        val (stringConstant, stringConstantType) =
            file.getAllPSIChildrenOfType<KtStringTemplateExpression>()
                .first()
                .let {
                    it to it.getType(bindingContext!!)!!
                }
        val newValue = rig!!.generateValueOfTypeAsExpression(stringConstantType)!!
        checker.replaceNodeIfPossible(stringConstant, newValue)
    }
}