package com.hse.network

import org.json.JSONObject

class RequestException(val name: String, val status: Int, message: String, val messageRu: String? = null, val messageEn: String? = null) : Throwable(message) {

    companion object {
        fun parse(json: JSONObject?): RequestException {
            if (json == null) return defaultException()
            val errorJson: JSONObject = if (json.has("error")) {
                json.optJSONObject("error") ?: return defaultException()
            } else json

            val (ru, en) = errorJson.optJSONObject("e")?.optJSONObject("message")?.run {
                this.optString("ru") to this.optString("en")
            } ?: null to null

            return RequestException(
                errorJson.optString("name"),
                errorJson.optInt("status"),
                errorJson.optString("message"),
                ru,
                en
            )
        }

        fun defaultException() = RequestException("UNKNOWN", -1, "Exception occurred")
    }
}