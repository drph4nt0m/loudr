package me.rhul.loudr

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Loudr Application entry point.
 *
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation and
 * initialise the application-level dependency injection component.
 *
 * No analytics, no telemetry, no network initialisation — intentional.
 */
@HiltAndroidApp
class LoudrApp : Application()
