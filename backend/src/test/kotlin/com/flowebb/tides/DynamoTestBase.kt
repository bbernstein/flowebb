package com.flowebb.tides

import com.flowebb.config.DynamoConfig
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient

abstract class DynamoTestBase {
    protected val mockDynamoClient: DynamoDbEnhancedClient = mockk(relaxed = true)

    @BeforeEach
    open fun setupDynamoTest() {
        System.setProperty("test.environment", "true")
        DynamoConfig.setTestClient(mockDynamoClient)
    }

    @AfterEach
    open fun tearDownDynamoTest() {
        try {
            if (System.getProperty("test.environment") == "true") {
                DynamoConfig.resetTestClient()
            }
        } finally {
            System.clearProperty("test.environment")
        }
    }
}
