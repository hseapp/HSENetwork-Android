package com.hse.network

import android.content.Context
import android.os.Build
import org.chromium.net.CronetEngine
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import javax.inject.Inject

interface Network {
    fun getCronetEngine(): CronetEngine
    fun getExecutor(): ScheduledExecutorService
    fun getAppContext(): Context
}

class NetworkImpl @Inject constructor(private val context: Context) : Network {
    private lateinit var userAgent: String
    private var cronet: CronetEngine? = null
    private val executor = Executors.newScheduledThreadPool(3)

    override fun getCronetEngine() = cronet!!
    override fun getExecutor() = executor
    override fun getAppContext() = context.applicationContext

    init {
        val packageManager = context.packageManager
        val info = packageManager.getPackageInfo(context.packageName, 0)
        setUserAgent("hse-${info.versionName}#${Build.MANUFACTURER}#${Build.MODEL}#${Build.VERSION.SDK_INT}")
    }

    fun setUserAgent(userAgent: String) {
        this.userAgent = userAgent
        initCronet()
    }

    private fun initCronet() {
        cronet?.shutdown()
        cronet = CronetEngine.Builder(context).apply {
            setUserAgent(userAgent)
            enableHttp2(true)
            setStoragePath(context.cacheDir.toString())
            enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 4000000L)
        }.build()
    }
}