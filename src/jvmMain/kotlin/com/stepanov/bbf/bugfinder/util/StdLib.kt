package com.stepanov.bbf.bugfinder.util

public inline fun <K, V> Map<out K, V>.forEach(action: (Map.Entry<K, V>) -> Unit): Unit {
    for (element in this) action(element)
}

public inline fun <T, R> Iterable<T>.flatMap(transform: (T) -> Iterable<R>): List<R> {
    return flatMapTo(ArrayList<R>(), transform)
}

