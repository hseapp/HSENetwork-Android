package com.hse.network

import org.json.JSONObject

class RequestException(val name: String, val status: Int, message: String) : Throwable(message) {

    companion object {
        fun parse(json: JSONObject?): RequestException {
            if (json == null) return defaultException()
            val errorJson: JSONObject = if (json.has("error")) {
                json.optJSONObject("error") ?: return defaultException()
            } else json

            return RequestException(
                errorJson.optString("name"),
                errorJson.optInt("status"),
                errorJson.optString("message")
            )
        }

        fun defaultException() = RequestException("UNKNOWN", -1, "Exception occurred")
    }
}