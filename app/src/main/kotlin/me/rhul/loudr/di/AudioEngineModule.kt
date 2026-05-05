package me.rhul.loudr.di

import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.engine.LoudnessEnhancerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the audio engine contract to its implementation.
 *
 * Binding [LoudnessEnhancerEngine] as the [AudioEngineRepository] here means
 * the implementation can be swapped (e.g., for a test fake) without touching
 * call sites. Root engine support would add a second binding with a qualifier.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioEngineModule {

    @Binds
    @Singleton
    abstract fun bindAudioEngineRepository(
        impl: LoudnessEnhancerEngine,
    ): AudioEngineRepository
}
