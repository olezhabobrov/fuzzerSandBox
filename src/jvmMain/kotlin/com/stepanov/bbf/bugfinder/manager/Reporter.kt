package com.stepanov.bbf.bugfinder.manager

interface Reporter {
    fun dump(bugs: List<Bug>)
}