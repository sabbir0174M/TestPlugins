package com.lagradost.cloudstream3

class ProviderRepository {
    fun loadProviders(): List<MainAPI> {
        return listOf(
            peachify.PeachifyProvider()
        )
    }
}
