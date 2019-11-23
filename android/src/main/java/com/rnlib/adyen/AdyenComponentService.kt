package com.rnlib.adyen

import com.adyen.checkout.base.model.payments.Amount
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.core.model.JsonUtils
import com.rnlib.adyen.service.CallResult
import com.rnlib.adyen.service.ComponentService
import com.adyen.checkout.redirect.RedirectComponent
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import java.io.IOException
import android.util.Log

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.RequestBody
import retrofit2.Retrofit
import com.rnlib.adyen.CheckoutApiService
import com.rnlib.adyen.ApiService
import com.rnlib.adyen.PaymentData
import com.rnlib.adyen.AdditionalData
import com.rnlib.adyen.AppServiceConfigData
import com.rnlib.adyen.AdyenPaymentModule
import java.net.URL

/**
 * This is just an example on how to make networkModule calls on the [DropInService].
 * You should make the calls to your own servers and have additional data or processing if necessary.
 */
class AdyenComponentService : ComponentService() {

    companion object {
        private val TAG = LogUtil.getTag()
        private val CONTENT_TYPE: MediaType = "application/json".toMediaType()
    }

    /*
    fun getCheckoutApi(baseURL: String): CheckoutApiService {
        return Retrofit.Builder()
            .baseUrl(baseURL)
            .client(ApiWorker.client)
            .addConverterFactory(ApiWorker.gsonConverter)
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
            .create(CheckoutApiService::class.java)
    }*/

    override fun makePaymentsCall(paymentComponentData: JSONObject): CallResult {
        Log.i(TAG, "makePaymentsCall")
        // Check out the documentation of this method on the parent DropInService class
        /*
                amount: {
                    value: Amt,
                    currency: 'EUR'
                },
                reference: "",
                shopperReference : shopper_internal_reference_id,
                shopperEmail : user.email,
                countryCode: countryCode.toUpperCase(),
                shopperLocale: shopperLocale,
                returnUrl: "",
                merchantAccount: MERCHANT_ACCOUNT
                additionalData : {
                        allow3DS2 : true,
                        executeThreeD : false
                }
        */
        
        val configData : AppServiceConfigData = AdyenPaymentModule.getAppServiceConfigData();
        val paymentRequest : JSONObject = AdyenPaymentModule.getPaymentData();
        paymentRequest.putOpt("paymentMethod", paymentComponentData.getJSONObject("paymentMethod"))
        paymentRequest.put("storePaymentMethod", paymentComponentData.getBoolean("storePaymentMethod"))
        paymentRequest.put("returnUrl", RedirectComponent.getReturnUrl(applicationContext))
        paymentRequest.put("channel", "Android")

        Log.i(TAG, "paymentComponentData - ${JsonUtils.indent(paymentComponentData)}")

        val requestBody = paymentRequest.toString().toRequestBody(CONTENT_TYPE)
        val call = ApiService.checkoutApi(configData.base_url).payments(configData.app_url_headers,requestBody)
        return handleResponse(call)
    }

    override fun makeDetailsCall(actionComponentData: JSONObject): CallResult {
        Log.d(TAG, "makeDetailsCall")

        Log.i(TAG, "payments/details/ - ${JsonUtils.indent(actionComponentData)}")
        val configData : AppServiceConfigData = AdyenPaymentModule.getAppServiceConfigData();
        val requestBody = actionComponentData.toString().toRequestBody(CONTENT_TYPE)
        val call = ApiService.checkoutApi(configData.base_url).details(configData.app_url_headers,requestBody)

        return handleResponse(call)
    }

    @Suppress("NestedBlockDepth")
    private fun handleResponse(call: Call<ResponseBody>): CallResult {
        return try {
            val response = call.execute()

            val byteArray = response.errorBody()?.bytes()
            if (byteArray != null) {
                Logger.e(TAG, "errorBody - ${String(byteArray)}")
            }

            val detailsResponse = JSONObject(response.body()?.string())

            if (response.isSuccessful) {
                if (detailsResponse.has("action")) {
                    CallResult(CallResult.ResultType.ACTION, detailsResponse.get("action").toString())
                } else {
                    Logger.d(TAG, "Final result - ${JsonUtils.indent(detailsResponse)}")
                    val successObj : JSONObject = JSONObject()
                    successObj.put("resultType","SUCCESS")
                    successObj.put("message",detailsResponse)
                    CallResult(CallResult.ResultType.FINISHED, successObj.toString())
                    /*
                    var resultCode = ""
                    if (detailsResponse.has("resultCode")) {
                        resultCode = detailsResponse.get("resultCode").toString()
                        if(resultCode == "Authorised" || resultCode == "Received" || resultCode == "PENDING"){
                            CallResult(CallResult.ResultType.FINISHED, detailsResponse.toString())
                        }else if(resultCode == "Refused" || resultCode == "Cancelled" || resultCode == "Error"){
                            CallResult(CallResult.ResultType.ERROR, "Transaction Refused/Cancelled/Error")
                        }else{
                            CallResult(CallResult.ResultType.ERROR, detailsResponse.toString())
                        }
                    }else{
                        CallResult(CallResult.ResultType.FINISHED, detailsResponse.toString())
                    }*/
                }
            } else {
                Logger.e(TAG, "FAILED - ${response.message()}")
                //CallResult(CallResult.ResultType.ERROR, response.message().toString())
                val errObj : JSONObject = JSONObject()
                errObj.put("resultType","ERROR")
                errObj.put("code","ERROR_GENERAL")
                errObj.put("message",response.message().toString())
                CallResult(CallResult.ResultType.FINISHED, errObj.toString())
            }
        } catch (e: IOException) {
            Logger.e(TAG, "IOException", e)
            //CallResult(CallResult.ResultType.ERROR, "IOException")
            val errObj : JSONObject = JSONObject()
            errObj.put("resultType","ERROR")
            errObj.put("code","ERROR_IOEXCEPTION")
            errObj.put("message","Unable to Connect to the Server")
            CallResult(CallResult.ResultType.FINISHED, errObj.toString())
        }
    }
}
