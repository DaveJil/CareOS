package com.example.api

import com.example.BuildConfig
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

data class PaystackInitializeRequest(
    val email: String,
    val amount: Long, // in kobo or cents
    val currency: String = "NGN",
    val reference: String,
    val callback_url: String = "https://careos.health/payment/callback"
)

data class PaystackInitializeData(
    val authorization_url: String,
    val access_code: String,
    val reference: String
)

data class PaystackInitializeResponse(
    val status: Boolean,
    val message: String,
    val data: PaystackInitializeData?
)

data class StripePaymentIntentRequest(
    val amount: Long, // in cents
    val currency: String = "usd",
    val description: String,
    val receipt_email: String
)

data class StripePaymentIntentResponse(
    val id: String?,
    val client_secret: String?,
    val status: String?,
    val amount: Long?
)

interface PaystackApiService {
    @POST("transaction/initialize")
    suspend fun initializeTransaction(
        @Header("Authorization") authorization: String,
        @Body request: PaystackInitializeRequest
    ): Response<PaystackInitializeResponse>
}

interface StripeApiService {
    @POST("v1/payment_intents")
    @Headers("Content-Type: application/x-www-form-urlencoded")
    suspend fun createPaymentIntent(
        @Header("Authorization") authorization: String,
        @Body body: String
    ): Response<StripePaymentIntentResponse>
}

object PaymentGatewayClient {
    private val paystackRetrofit = Retrofit.Builder()
        .baseUrl("https://api.paystack.co/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val stripeRetrofit = Retrofit.Builder()
        .baseUrl("https://api.stripe.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val paystackService: PaystackApiService = paystackRetrofit.create(PaystackApiService::class.java)
    val stripeService: StripeApiService = stripeRetrofit.create(StripeApiService::class.java)

    suspend fun initializePaystack(
        email: String,
        amountInNaira: Double,
        reference: String,
        apiKey: String
    ): PaystackInitializeResponse? {
        val amountInKobo = (amountInNaira * 100).toLong()
        val request = PaystackInitializeRequest(
            email = email,
            amount = amountInKobo,
            currency = "NGN",
            reference = reference
        )
        return try {
            val response = paystackService.initializeTransaction(
                authorization = "Bearer ${apiKey.ifBlank { BuildConfig.PAYSTACK_SECRET_KEY }}",
                request = request
            )
            response.body()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createStripePaymentIntent(
        email: String,
        amountInUsd: Double,
        description: String,
        apiKey: String
    ): StripePaymentIntentResponse? {
        val amountInCents = (amountInUsd * 100).toLong()
        val bodyStr = "amount=$amountInCents&currency=usd&receipt_email=$email&description=$description"
        return try {
            val response = stripeService.createPaymentIntent(
                authorization = "Bearer ${apiKey.ifBlank { BuildConfig.STRIPE_SECRET_KEY }}",
                body = bodyStr
            )
            response.body()
        } catch (e: Exception) {
            null
        }
    }
}
