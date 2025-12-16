package com.example.spendsense.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// 1. Data Model for API Response
data class CurrencyResponse(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

// 2. API Interface
interface ExchangeRateApi {
    @GET("latest")
    suspend fun getRates(
        @Query("from") fromCurrency: String,
        @Query("to") toCurrency: String,
        @Query("amount") amount: Double
    ): CurrencyResponse
}

// 3. Singleton Instance
object RetrofitClient {
    private const val BASE_URL = "https://api.frankfurter.app/"

    val api: ExchangeRateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }
}