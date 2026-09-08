package com.justjdupuis.summonpro.api

import com.google.gson.annotations.SerializedName
import com.justjdupuis.summonpro.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

object TeslaApi {
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.FLEET_API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    interface Service {
        @GET("api/1/vehicles")
        suspend fun getVehicleList(
            @Header("Authorization") token: String,
        ): VehicleListResponse

        @GET("api/1/vehicles/{vehicle_tag}")
        suspend fun getVehicleInfo(
            @Header("Authorization") token: String,
            @Path("vehicle_tag") vehicleTag: String,
        ): VehicleResponse

        @GET("api/1/vehicles/{vehicle_tag}/vehicle_data?endpoints=location_data")
        suspend fun getVehicleLocation(
            @Header("Authorization") token: String,
            @Path("vehicle_tag") vehicleTag: String,
        ): VehicleDataResponse
    }

    val service: Service = retrofit.create(Service::class.java)

    data class VehicleResponse(val response: Vehicle)
    data class VehicleDataResponse(val response: VehicleData)
    data class VehicleData(
        @SerializedName("drive_state") val driveState: DriveState?,
    )
    data class DriveState(
        val latitude: Double?,
        val longitude: Double?,
        val heading: Double?,
        val timestamp: Long?,
    )
    data class VehicleListResponse(
        val response: List<Vehicle>,
        val count: Int
    )

    data class Vehicle(
        @SerializedName("id") val id: Long,
        @SerializedName("vehicle_id") val vehicleId: Long,
        @SerializedName("vin") val vin: String,
        @SerializedName("color") val color: Any?, // can be null
        @SerializedName("access_type") val accessType: String,
        @SerializedName("display_name") val displayName: String,
        @SerializedName("option_codes") val optionCodes: String,
        @SerializedName("granular_access") val granularAccess: Any?,
        @SerializedName("tokens") val tokens: Any?, // use Any? or JsonObject if mixed/unknown
        @SerializedName("state") val state: String,
        @SerializedName("in_service") val inService: Boolean,
        @SerializedName("id_s") val idS: String,
        @SerializedName("calendar_enabled") val calendarEnabled: Boolean,
        @SerializedName("api_version") val apiVersion: Int,
        @SerializedName("backseat_token") val backseatToken: Any?,
        @SerializedName("backseat_token_updated_at") val backseatTokenUpdatedAt: Any?,
        @SerializedName("ble_autopair_enrolled") val bleAutopairEnrolled: Boolean
    )
}
