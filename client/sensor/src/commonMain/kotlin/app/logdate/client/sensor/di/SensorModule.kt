package app.logdate.client.sensor.di

import org.koin.core.module.Module

/**
 * Provides sensor-related dependencies across all platforms.
 * 
 * This module combines platform-specific sensor implementations with
 * common components.
 */
expect val sensorModule: Module
