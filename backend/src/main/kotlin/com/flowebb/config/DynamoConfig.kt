package com.flowebb.config

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import java.net.URI
import org.slf4j.LoggerFactory

object DynamoConfig {
    private var _enhancedClient: DynamoDbEnhancedClient? = null
    private var isTestEnvironment = false
    private val logger = LoggerFactory.getLogger(DynamoConfig::class.java)

    val enhancedClient: DynamoDbEnhancedClient
        get() = _enhancedClient ?: createClient()

    // Add these methods for testing
    fun setTestClient(client: DynamoDbEnhancedClient) {
        _enhancedClient = client
        isTestEnvironment = true
    }

    fun resetTestClient() {
        _enhancedClient = null
        isTestEnvironment = false
    }

    private fun createClient(): DynamoDbEnhancedClient {
        logger.debug("Creating DynamoDB client. isTestEnvironment: $isTestEnvironment")
        if (isTestEnvironment) {
            throw IllegalStateException("Test client not set. Call setTestClient() first in test environment.")
        }

        val isLocalDevelopment = System.getenv("AWS_SAM_LOCAL") == "true"

        return if (isLocalDevelopment) {
            // Local DynamoDB configuration
            DynamoDbEnhancedClient.builder()
                .dynamoDbClient(
                    DynamoDbClient.builder()
                        .httpClient(UrlConnectionHttpClient.builder().build())
                        .endpointOverride(URI("http://dynamodb-local:8000"))
                        .region(Region.of(System.getenv("AWS_REGION") ?: "us-west-2"))
                        .credentialsProvider(
                            StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("local", "local")
                            )
                        )
                        .build()
                )
                .build()
                .also { _enhancedClient = it }
        } else {
            // Production AWS configuration
            DynamoDbEnhancedClient.builder()
                .dynamoDbClient(
                    DynamoDbClient.builder()
                        .httpClient(UrlConnectionHttpClient.builder().build())
                        .build()
                )
                .build()
                .also { _enhancedClient = it }
        }
    }
}
