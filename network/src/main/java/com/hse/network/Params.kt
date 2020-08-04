package com.hse.network

import androidx.collection.ArrayMap
import org.json.JSONObject
import java.net.URLEncoder

class Params {
    var toJson = false
    var json: String? = null
    val params = ArrayMap<String, Any>()

    fun put(key: String, value: Any?) {
        if (value == null || value.toString().isEmpty()) return
        params[key] = value
    }

    override fun toString(): String {
        if (json != null) return json!!

        if (toJson) {
            val jsonObject = JSONObject()
            for ((key, value) in params) {
                jsonObject.put(key, value)
            }
            return jsonObject.toString()
        } else {
            val stringBuilder = StringBuilder()
            for ((key, value) in params) {
                stringBuilder.append("&").append(key).append("=")
                    .append(URLEncoder.encode(value.toString(), "utf-8"))
            }
            val result = stringBuilder.toString()
            if (result.isEmpty()) {
                return ""
            }
            return result.subSequence(1, result.length) as String
        }
    }

    fun toBytesArray(): ByteArray {
        return toString().toByteArray(Charsets.UTF_8)
    }

    fun isEmpty() = params.isEmpty && json == null
}