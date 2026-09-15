package com.sonara.desktop.data

import java.security.MessageDigest

object LastFmSigner {
    private val SKIP = setOf("format", "callback", "api_sig")

    fun sign(params: Map<String, String>, secret: String): String {
        val base = params
            .filterKeys { it !in SKIP }
            .toSortedMap()
            .entries
            .joinToString(separator = "") { (k, v) -> k + v } + secret
        return md5(base)
    }

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
