package com.flowebb.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mu.KotlinLogging

class HttpClientService {
    private val logger = KotlinLogging.logger {}
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
                encodeDefaults = true
            })
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }

        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }

    suspend fun <T> get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
        transform: suspend (HttpResponse) -> T
    ): T = withContext(Dispatchers.IO) {
        logger.debug { "Making GET request to $url" }
        logger.debug { "Query parameters: $queryParams" }

        try {
            val response = client.get(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                queryParams.forEach { (key, value) ->
                    parameter(key, value)
                }
            }

            logger.debug { "Response status: ${response.status}" }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error { "API call failed: $errorBody" }
                throw ApiException(response.status, errorBody)
            }

            transform(response).also {
                logger.debug { "Successfully transformed response" }
            }
        } catch (e: Exception) {
            logger.error(e) { "API call failed" }
            throw e
        }
    }

    @Suppress("unused")
    suspend fun <T> post(
        url: String,
        body: Any,
        headers: Map<String, String> = emptyMap(),
        transform: suspend (HttpResponse) -> T
    ): T = withContext(Dispatchers.IO) {
        logger.debug { "Making POST request to $url" }

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }

            logger.debug { "Response status: ${response.status}" }
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error { "API call failed: $errorBody" }
                throw ApiException(response.status, errorBody)
            }

            transform(response).also {
                logger.debug { "Successfully transformed response" }
            }
        } catch (e: Exception) {
            logger.error(e) { "API call failed" }
            throw e
        }
    }

    class ApiException(
        status: HttpStatusCode,
        errorBody: String
    ) : Exception("API call failed with status $status: $errorBody")
}
