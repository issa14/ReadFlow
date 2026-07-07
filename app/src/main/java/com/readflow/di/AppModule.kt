package com.readflow.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Module Hilt principal.
 * Sera enrichi avec les bindings des repositories et services.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
