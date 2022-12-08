package com.stepanov.bbf.bugfinder.projectfuzzer

import com.stepanov.bbf.bugfinder.util.getAllChildrenOfCurLevel
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfTypeOfFirstLevel
import com.stepanov.bbf.bugfinder.util.getFirstParentOfType
import org.jetbrains.kotlin.psi.*

class ClassTable(private val klass: KtClassOrObject) {
    val packageDirective: KtPackageDirective?
    val fields: List<KtProperty>
    val funcs: List<KtNamedFunction>

    init {
        packageDirective = klass.getFirstParentOfType<KtFile>()?.packageDirective
        fields = getAllKlassFields()
        funcs = getAllFuncs()
    }

    fun getAllKlassFields(): List<KtProperty> =
        when (klass) {
            is KtClass -> klass.getProperties()
            else -> klass.getAllPSIChildrenOfTypeOfFirstLevel<KtProperty>()
        }

    fun getAllFuncs(): List<KtNamedFunction> =
        klass.getAllPSIChildrenOfTypeOfFirstLevel<KtNamedFunction>()


}