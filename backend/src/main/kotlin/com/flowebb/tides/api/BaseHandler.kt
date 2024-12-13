package com.flowebb.tides.api

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

abstract class BaseHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    protected val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override fun handleRequest(
        input: APIGatewayProxyRequestEvent,
        context: Context
    ): APIGatewayProxyResponseEvent {
        return try {
            runBlocking {
                withContext(Dispatchers.Default) {
                    handleRequestSuspend(input, context)
                }
            }
        } catch (e: Exception) {
            error("Internal Server Error: ${e.message}")
        }
    }

    abstract suspend fun handleRequestSuspend(
        input: APIGatewayProxyRequestEvent,
        context: Context
    ): APIGatewayProxyResponseEvent

    protected fun success(response: ApiResponse): APIGatewayProxyResponseEvent {
        return try {
            APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(json.encodeToString<ApiResponse>(response))
                .withHeaders(mapOf("Content-Type" to "application/json"))
        } catch (e: Exception) {
            error("Serialization error: ${e.message}")
        }
    }

    protected fun error(message: String): APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent()
            .withStatusCode(500)
            .withBody(json.encodeToString<ApiResponse>(ErrorResponse(error = message)))
            .withHeaders(mapOf("Content-Type" to "application/json"))
    }
}
