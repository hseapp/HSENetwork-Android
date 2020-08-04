package com.hse.network

class RequestException(val name: String, val status: Int, message: String) : Throwable(message)