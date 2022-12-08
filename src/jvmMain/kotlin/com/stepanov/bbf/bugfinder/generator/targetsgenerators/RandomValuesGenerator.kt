@file:Suppress("IMPLICIT_CAST_TO_ANY")

package com.stepanov.bbf.bugfinder.util

import java.util.*
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator.generateRandomType
import com.stepanov.bbf.bugfinder.util.kcheck.asCharSequence
import com.stepanov.bbf.bugfinder.util.kcheck.nextChar
import com.stepanov.bbf.bugfinder.util.kcheck.nextInRange
import com.stepanov.bbf.bugfinder.util.kcheck.nextString

const val LINE_SIZE = 5
const val RANDOM_CONST = 5

fun generateDefValuesAsString(type: String): String {
    return when {
        type.let { it == "UByte" || it == "UShort" || it == "UInt" || it == "ULong" } ->
            "${generateDefValuesAsString(type.substring(1))}.to$type()".substringAfter('-')
        type.let { it == "UByte?" || it == "UShort?" || it == "UInt?" || it == "ULong?" } ->
            "${generateDefValuesAsString(type.substring(1))}.to$type()".substringAfter('-')
        type == "Any" -> generateDefValuesAsString(generateRandomType())
        type == "Any?" -> generateDefValuesAsString(generateRandomType())
        type == "String" -> "\"${generateDefValuesForDefaultTypes<String>(type)}\""
        type == "Number" -> generateDefValuesForDefaultTypes<Int>("Int").toString()
        type == "Number?" -> generateDefValuesForDefaultTypes<Int>("Int").toString()
        type == "Int" -> generateDefValuesForDefaultTypes<Int>(type).toString()
        type == "Double" -> generateDefValuesForDefaultTypes<Double>(type).toString()
        type == "Boolean" -> generateDefValuesForDefaultTypes<Boolean>(type).toString()
        type == "Long" -> generateDefValuesForDefaultTypes<Long>(type).toString()
        type == "Short" -> generateDefValuesForDefaultTypes<Short>(type).toString()
        type == "Char" -> "\'${generateDefValuesForDefaultTypes<Char>(type)}\'"
        type == "Byte" -> generateDefValuesForDefaultTypes<Byte>(type).toString()
        type == "Float" -> generateDefValuesForDefaultTypes<Float>(type).toString()+"f"
        type == "String?" -> "\"${generateDefValuesForDefaultTypes<String>(type)}\""
        type == "Int?" -> generateDefValuesForDefaultTypes<Int>(type).toString()
        type == "Double?" -> generateDefValuesForDefaultTypes<Double>(type).toString()
        type == "Boolean?" -> generateDefValuesForDefaultTypes<Boolean>(type).toString()
        type == "Long?" -> generateDefValuesForDefaultTypes<Long>(type).toString()
        type == "Short?" -> generateDefValuesForDefaultTypes<Short>(type).toString()
        type == "Char?" -> "\'${generateDefValuesForDefaultTypes<Char>(type)}\'"
        type == "Byte?" -> generateDefValuesForDefaultTypes<Byte>(type).toString()
        type == "Float?" -> generateDefValuesForDefaultTypes<Float>(type).toString()+"f"
        //type == "CharSequence" -> "\"${generateDefValuesForDefaultTypes<String>(type)}\""
        type.startsWith("List") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "listOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "listOf()"
        }
        type.startsWith("ArrayList") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "arrayListOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "arrayListOf()"
        }
        type.startsWith("MutableList") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "mutableListOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "mutableListOf()"
        }
        type.startsWith("Set") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "setOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "setOf()"
        }
        type.startsWith("Iterable") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "arrayListOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "arrayListOf()"
        }
        type.startsWith("HashSet") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "hashSetOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "hashSetOf()"
        }
        type.startsWith("LinkedHashSet") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "linkedSetOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "linkedSetOf()"
        }
        type.startsWith("MutableSet") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "mutableSetOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "mutableSetOf()"
        }
        type.startsWith("Array") -> {
            if (type.contains('<'))
                createDefaultValueForContainer(
                    "arrayOf(",
                    type.substringAfter('<').substringBeforeLast('>')
                )
            else "arrayOf()"
        }
        type.startsWith("HashMap") -> {
            createDefaultValueWithToOperator(type, /*Random().nextInt(randomConst)*/5)
        }
        type.startsWith("MutableMap") -> {
            createDefaultValueWithToOperator(type, /*Random().nextInt(randomConst)*/5)
        }
        type.startsWith("LinkedHashMap") -> {
            createDefaultValueWithToOperator(
                type.replaceFirst(
                    "LinkedHashMap",
                    "LinkedMap"
                ), /*Random().nextInt(randomConst)*/5
            )
        }
        type.startsWith("Pair") -> {
            createDefaultValueWithToOperator(type, 1)
        }
        type.startsWith("Map") -> {
            createDefaultValueWithToOperator(type, /*Random().nextInt(randomConst)*/5)
        }
        type.substringBefore('<').contains("Array") ->
            type.substringBefore("Array").let { createDefaultValueForContainer("${it.toLowerCase()}ArrayOf(", it) }
        type.startsWith("Function") -> {
            "{${generateDefValuesAsString(type.substringAfter('<').substringBeforeLast('>').substringAfterLast(',').trim())}}"
        }
        else -> {
            //println("Unsupported type : $type")
            ""
        }
    }
}


@Suppress("UNCHECKED_CAST")
private fun <T> generateDefValuesForDefaultTypes(type: String): T =
    when (type) {
        "Int"  -> Random().nextInRange(-100, 100)
        "Double" -> Random().nextInRange(-100.0, 100.0)
        "Boolean" -> Random().nextBoolean()
        "Long" -> Random().nextInRange(-100, 100)
        "Short" -> Random().nextInRange(-100, 100)
        "Char" -> Random().nextChar()
        "Byte" -> Random().nextInRange(-100, 100)
        "Float" -> Random().nextInRange(-100.0f, 100.0f)
        "Int?"  -> Random().nextInRange(-100, 100)
        "Double?" -> Random().nextInRange(-100.0, 100.0)
        "Boolean?" -> Random().nextBoolean()
        "Long?" -> Random().nextInRange(-100, 100)
        "Short?" -> Random().nextInRange(-100, 100)
        "Char?" -> Random().nextChar()
        "Byte?" -> Random().nextInRange(-100, 100)
        "Float?" -> Random().nextInRange(-100.0f, 100.0f)
        else -> Random().nextString(
            ('a'..'z').asCharSequence(),
            LINE_SIZE, LINE_SIZE + 1
        )
    } as T


private fun getLeftAndRightTypes(type: String): Pair<String, String> {
    var left = ""
    var right = type
    for (t in type) {
        right = right.drop(1)
        if (t == ',') {
            val sumL = left.count { it == '>' || it == '<' }
            val sumR = right.count { it == '>' || it == '<' }
            if (sumL == sumR) {
                return left.dropWhile { it != '<' }.drop(1).trim() to right.dropLast(1).trim()
                //println("TYPE LEFT = ${left.dropWhile { it != '<' }.drop(1).trim()} RIGHT = ${right.dropLast(1).trim()}")
            }
        }
        left += t
    }
    return "" to ""
}

private fun createDefaultValueWithToOperator(type: String, size: Int): String {
    val res = StringBuilder()
    val types = getLeftAndRightTypes(type)
    if (size == 1)
        res.append("(")
    else
        res.append("${type.decapitalize().substringBefore('<')}Of(")
    repeat(size) {
        res.append(generateDefValuesAsString(types.first))
        res.append(" to ")
        res.append(generateDefValuesAsString(types.second))
        res.append(", ")
    }
    res.replace(res.length - 2, res.length, ")")
    return res.toString()
}

private fun createDefaultValueForContainer(name: String, typeParam: String): String {
    //TODO !!!!!!!!! REPAIR IT FOR {}
    if (typeParam.contains('{')) return ""
    val values = StringBuilder()
    values.append(name)
    repeat(Random().nextInt(RANDOM_CONST) + 1) {
        values.append(
            "${generateDefValuesAsString(
                typeParam
            )}, "
        )
    }
    values.replace(values.length - 2, values.length, ")")
    return values.toString()
}