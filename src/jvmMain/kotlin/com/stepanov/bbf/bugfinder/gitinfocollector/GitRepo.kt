package com.stepanov.bbf.bugfinder.gitinfocollector

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.system.exitProcess

class GitRepo(orgName: String, projectName: String) {
    private val client = OkHttpClient()
    private val url = "https://api.github.com/repos/$orgName/$projectName"

    fun getPatches(commitSHA: String): List<FilePatch> {
        val request = Request.Builder().url("$url/commits/$commitSHA").build()
        val responseBody = client.newCall(request).execute().body ?: return listOf()
        val json = Json.parseToJsonElement(responseBody.string())
        val files = json.jsonObject["files"]?.jsonArray
        val sources =
            files
                ?.map { it.jsonObject["raw_url"]?.jsonPrimitive?.content ?: "" }
                ?.map {
                    it.substringAfter("$commitSHA/") to Request.Builder().url(it).build().let { req ->
                        client.newCall(req).execute().body?.string() ?: ""
                    }
                } ?: return listOf()
        val patches = files.map { it.jsonObject["patch"]?.jsonPrimitive?.content ?: "" }
        return sources.mapIndexed { index, s ->
            FilePatch(
                s.first,
                s.second,
                PatchParser.parsePatchesFromString(patches[index])
            )
        }
    }

}