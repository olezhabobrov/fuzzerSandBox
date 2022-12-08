package com.stepanov.bbf.bugfinder.util.decompiler

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

fun ZipFile.copyContentTo(dir: String, filterFun: (ZipEntry) -> Boolean = { true }) {
    val bufSize = 4096
    for (entry in this.entries().toList().filter { filterFun(it) }) {
        val stream = this.getInputStream(entry)
        if (entry.name.contains('/')) {
            File("$dir/${entry.name.substringBeforeLast('/')}").mkdirs()
        }
        val bos = BufferedOutputStream(FileOutputStream("$dir/${entry.name}"))
        val bytesIn = ByteArray(bufSize)
        var read = 0
        while (read != -1) {
            bos.write(bytesIn, 0, read)
            read = stream.read(bytesIn)
        }
        bos.close()
    }
}