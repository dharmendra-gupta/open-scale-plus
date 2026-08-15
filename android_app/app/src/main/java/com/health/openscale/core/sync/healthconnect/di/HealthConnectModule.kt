/*
 * openScale
 * Copyright (C) 2026 openScale+ Dharmendra Gupta
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.sync.healthconnect.di

import com.health.openscale.core.facade.SettingsFacade
import com.health.openscale.core.sync.healthconnect.HealthConnectApi
import com.health.openscale.core.sync.healthconnect.HealthConnectApiClient
import com.health.openscale.core.sync.healthconnect.HealthConnectSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface HealthConnectModule {

    @Binds
    @Singleton
    fun bindHealthConnectApi(client: HealthConnectApiClient): HealthConnectApi

    companion object {
        @Provides
        @Singleton
        fun provideHealthConnectSettings(settings: SettingsFacade): HealthConnectSettings = settings
    }
}
