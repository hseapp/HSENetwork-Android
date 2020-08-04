package com.hse.network

import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
abstract class NetworkModule {
    @Singleton
    @Binds
    abstract fun network(network: NetworkImpl): Network

}