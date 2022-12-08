//package com.stepanov.bbf.bugfinder.generator.subjectgenerator
//
//import com.stepanov.bbf.bugfinder.util.generateRandomType
//import com.stepanov.bbf.bugfinder.util.getRandomVariableName
//import java.util.*
//
//class Parameter(fixedType: String = "") {
//    val name: String = Random().getRandomVariableName(5)
//    var type: String
//        private set
//
//    init {
//        type = if (fixedType.isEmpty()) generateRandomType() else fixedType
//        //We need to somehow bound type?
//        while (type.length > 50) type = generateRandomType()
//    }
//
//    override fun toString(): String = "$name: $type"
//}