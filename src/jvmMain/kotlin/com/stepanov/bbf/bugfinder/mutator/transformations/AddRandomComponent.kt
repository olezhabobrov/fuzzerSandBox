package com.stepanov.bbf.bugfinder.mutator.transformations

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomFunctionGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.generators.RandomPropertyGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.abi.gstructures.GClass
import com.stepanov.bbf.bugfinder.mutator.transformations.util.FileFieldsTable
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import com.stepanov.bbf.reduktor.util.initBodyByTODO
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import kotlin.random.Random

class AddRandomComponent : Transformation() {

    private val ktFile = file as KtFile
    private val ctx = PSICreator.analyze(ktFile, project)
    private var randomInstanceGenerator: RandomInstancesGenerator? = null
    private val rtg = RandomTypeGenerator
    private lateinit var table: FileFieldsTable

    override fun transform() {
        if (ctx == null) return
        randomInstanceGenerator = RandomInstancesGenerator(ktFile, ctx)
        table = FileFieldsTable(ktFile, ctx)
        rtg.setFileAndContext(ktFile, ctx)
        repeat(Random.nextInt(20, 50)) {
            val randomClass = file.getAllPSIChildrenOfType<KtClassOrObject>()/*.first()*/.randomOrNull() ?: return
            val gRandomClass = GClass.fromPsi(randomClass)
            if (Random.nextBoolean()) addRandomProperty(randomClass, gRandomClass)
            else addRandomFunction(randomClass, gRandomClass)
        }
    }

    private fun addRandomTopLevelFunction() {
        val randomFunctionGenerator = RandomFunctionGenerator(ktFile, ctx!!)
        val generatedFunc = randomFunctionGenerator.generate() as? KtNamedFunction ?: return
        //println(generatedFunc.text)
        return
    }

    private fun addRandomProperty(psiClass: KtClassOrObject, gClass: GClass) {
        val randomPropertyGenerator = RandomPropertyGenerator(ktFile, ctx!!, gClass)
        val generatedProp =
            if (Random.getTrue(30)) randomPropertyGenerator.generateInterestingProperty(psiClass)?.first as? KtProperty
            else randomPropertyGenerator.generate() as? KtProperty
        if (generatedProp == null) return
        //println(generatedProp.text + "\n")
        val addedProperty = psiClass.addPsiToBody(generatedProp) as? KtProperty ?: return
        if (!checker.checkCompiling()) {
            //println("CANT COMPILE =(")
            addedProperty.delete()
            return
        }
        val updatedCtx = PSICreator.analyze(ktFile) ?: return
        val propertyType = addedProperty.typeReference?.getAbbreviatedTypeOrType(updatedCtx) ?: return
        val initializer = addedProperty.initializer
        if (initializer != null && initializer.text?.contains("TODO()") == true) {
            generateValueOfType(propertyType)?.let { initializer.replaceThis(it) }
        }
        val getter = addedProperty.getter
        if (getter != null && getter.hasInitializer() && getter.text?.contains("TODO()") == true) {
            generateValueOfType(propertyType)?.let { getter.initializer!!.replaceThis(it) }
        }
        val setter = addedProperty.setter
        if (setter != null && setter.hasInitializer() && !setter.isPrivate()) {
            generateValueOfType(propertyType)?.let { addedProperty.setInitializer(it) }
            generateValueOfType(propertyType)?.let { setter.initializer!!.replaceThis(it) }
        }
        if (!checker.checkCompiling()) {
            addedProperty.delete()
        }
    }

    private fun generateValueOfType(type: KotlinType): KtExpression? {
        val newValue = randomInstanceGenerator!!.generateValueOfType(type)
        return try {
            Factory.psiFactory.createExpressionIfPossible(newValue)
        } catch (e: Exception) {
            null
        } catch (e: Error) {
            null
        }
    }

    //TODO open and abstract functions?????
    private fun addRandomFunction(psiClass: KtClassOrObject, gClass: GClass) {
        val randomFunctionGenerator = RandomFunctionGenerator(ktFile, ctx!!, gClass)
        val generatedFunc = randomFunctionGenerator.generate() as? KtNamedFunction ?: return
        //println(generatedFunc.text)
        val addedGeneratedFunc = psiClass.addPsiToBody(generatedFunc) as? KtNamedFunction ?: return
        if (!checker.checkCompiling()) {
            addedGeneratedFunc.delete()
            return
        }
        val updatedCtx = PSICreator.analyze(ktFile) ?: return
        table = FileFieldsTable(ktFile, updatedCtx)
        val addedFuncDescriptor =
            addedGeneratedFunc.getDeclarationDescriptorIncludingConstructors(updatedCtx) as? FunctionDescriptor
                ?: return
        val tableEntry = table.getEntry(addedFuncDescriptor) ?: return
        val availableFields = table.getAvailableDescriptors(tableEntry)
        val rtvType = addedFuncDescriptor.returnType ?: return
        val funBody =
            if (rtvType.isTypeParameter()) {
                val randomFieldWithSameType =
                    availableFields
                        .filter { it.type.isTypeParameter() && rtvType.getNameWithoutError() == it.type.getNameWithoutError() }
                        .randomOrNull()
                        ?.value as? KtNamedDeclaration
                randomFieldWithSameType?.name ?: ""
            } else randomInstanceGenerator!!.generateValueOfType(rtvType)
        //println("BODY = $funBody")
        if (funBody.isEmpty()) return
        try {
            addedGeneratedFunc.initBodyByValue(Factory.psiFactory, funBody)
        } catch (e: Exception) {
            addedGeneratedFunc.initBodyByTODO(Factory.psiFactory)
        } catch (e: Error) {
            addedGeneratedFunc.initBodyByTODO(Factory.psiFactory)
        }
        if (!checker.checkCompiling()) {
            addedGeneratedFunc.delete()
        }
    }
}