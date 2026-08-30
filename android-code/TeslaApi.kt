package com.venkat.tesladashboard

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class Reading(
    val timestamp: String?,
    val battery_level: Int?,
    val charging_state: String?,
    val charge_energy_added: Double?,
    val odometer: Double?
)

data class StatusResponse(val latest: Reading?)

data class DailySummary(
    val date: String?,
    val charging_minutes: Int?,
    val kwh_added: Double?,
    val km_driven: Double?,
    val start_odometer: Double?,
    val end_odometer: Double?,
    val start_battery: Int?,
    val end_battery: Int?
)

data class PeriodSummary(
    val days_count: Int?,
    val total_km: Double?,
    val total_kwh: Double?,
    val start_battery: Int?,
    val end_battery: Int?
)

interface TeslaApi {
    @GET("status")
    suspend fun getStatus(@Header("x-api-key") apiKey: String): StatusResponse

    @GET("summary/today")
    suspend fun getTodaySummary(@Header("x-api-key") apiKey: String): DailySummary

    @GET("summary/range")
    suspend fun getRangeSummary(
        @Header("x-api-key") apiKey: String,
        @Query("days") days: Int
    ): List<DailySummary>

    @GET("summary/week")
    suspend fun getWeekSummary(@Header("x-api-key") apiKey: String): PeriodSummary

    @GET("summary/month")
    suspend fun getMonthSummary(@Header("x-api-key") apiKey: String): PeriodSummary
}
