package com.flowebb.config

import mu.KotlinLogging
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

object DynamoConfig {
    private val logger = KotlinLogging.logger {}

    // Environment detection
    private val isTestEnvironment: Boolean
        get() = System.getProperty("test.environment") == "true"

    private val isLocalDevelopment: Boolean
        get() =
            System.getenv("AWS_SAM_LOCAL") == "true" ||
                (!System.getenv("DYNAMODB_ENDPOINT").isNullOrBlank())

    // Private variables for client management
    private var testClient: DynamoDbEnhancedClient? = null
    private val productionClient by lazy { createProductionClient() }

    // Public access to the client
    val enhancedClient: DynamoDbEnhancedClient
        get() =
            when {
                isTestEnvironment ->
                    testClient ?: throw IllegalStateException(
                        "Test client not set. Call setTestClient() first in test environment.",
                    )

                isLocalDevelopment -> createLocalClient()
                else -> productionClient
            }

    // Testing support
    fun setTestClient(client: DynamoDbEnhancedClient) {
        if (!isTestEnvironment) {
            throw IllegalStateException("Cannot set test client in non-test environment")
        }
        testClient = client
        logger.debug("Test client has been set")
    }

    fun resetTestClient() {
        if (!isTestEnvironment) {
            throw IllegalStateException("Cannot reset test client in non-test environment")
        }
        testClient = null
        logger.debug("Test client has been reset")
    }

    private fun createLocalClient(): DynamoDbEnhancedClient {
        val endpoint =
            System.getenv("DYNAMODB_ENDPOINT")?.takeIf { it.isNotBlank() }
                ?: "http://dynamodb-local:8000"
        val region = System.getenv("AWS_REGION") ?: "us-west-2"
        val accessKeyId = System.getenv("AWS_ACCESS_KEY_ID") ?: "local"
        val secretKey = System.getenv("AWS_SECRET_ACCESS_KEY") ?: "local"

        logger.debug("Initializing local DynamoDB client:")
        logger.debug("  Endpoint: $endpoint")
        logger.debug("  Region: $region")
        logger.debug("  Using local credentials")

        return DynamoDbEnhancedClient
            .builder()
            .dynamoDbClient(
                DynamoDbClient
                    .builder()
                    .httpClient(UrlConnectionHttpClient.builder().build())
                    .endpointOverride(URI(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(
                        StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretKey),
                        ),
                    ).build(),
            ).build()
            .also { logger.debug("Successfully created local DynamoDB client") }
    }

    private fun createProductionClient(): DynamoDbEnhancedClient {
        logger.debug("Initializing production DynamoDB client")

        return DynamoDbEnhancedClient
            .builder()
            .dynamoDbClient(
                DynamoDbClient
                    .builder()
                    .httpClient(UrlConnectionHttpClient.builder().build())
                    .build(),
            ).build()
            .also { logger.debug("Successfully created production DynamoDB client") }
    }
}
