package com.flowebb.tides.cache

import com.flowebb.config.DynamoConfig
import com.flowebb.tides.DynamoTestBase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TidePredictionCacheTest : DynamoTestBase() {
    private lateinit var mockTable: DynamoDbTable<TidePredictionRecord>
    private lateinit var cache: TidePredictionCache

    @BeforeEach
    fun setup() {
        mockTable = mockk()

        every {
            mockDynamoClient.table(
                "tide-predictions-cache",
                TableSchema.fromBean(TidePredictionRecord::class.java),
            )
        } returns mockTable

        DynamoConfig.setTestClient(mockDynamoClient)
        cache = TidePredictionCache()
    }

    @Test
    fun `getPredictions returns cached data when valid`() =
        runTest {
            val now = System.currentTimeMillis()
            val date = LocalDate.of(2024, 3, 20)
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)
            val record =
                TidePredictionRecord(
                    stationId = "TEST1",
                    date = dateStr,
                    stationType = "R",
                    predictions = listOf(CachedPrediction(now, 5.0)),
                    lastUpdated = now,
                    ttl = now + 7 * 24 * 60 * 60 * 1000,
                )

            every {
                mockTable.getItem(match<Key> { true })
            } returns record

            val result = cache.getPredictions("TEST1", date)
            assertEquals(record, result)
        }

    @Test
    fun `getPredictions returns null for expired cache`() =
        runTest {
            val expired = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000) // 8 days old
            val date = LocalDate.of(2024, 3, 20)
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)
            val record =
                TidePredictionRecord(
                    stationId = "TEST1",
                    date = dateStr,
                    stationType = "R",
                    predictions = listOf(CachedPrediction(expired, 5.0)),
                    lastUpdated = expired,
                    ttl = expired + 7 * 24 * 60 * 60 * 1000,
                )

            every {
                mockTable.getItem(match<Key> { true })
            } returns record

            val result = cache.getPredictions("TEST1", date)
            assertNull(result)
        }

    @Test
    fun `savePredictionsBatch handles multiple records correctly`() =
        runTest {
            val now = System.currentTimeMillis()
            val records =
                listOf(
                    TidePredictionRecord(
                        stationId = "TEST1",
                        date = "2024-03-20",
                        stationType = "R",
                        predictions = listOf(CachedPrediction(now, 5.0)),
                        lastUpdated = now,
                        ttl = now + 7 * 24 * 60 * 60 * 1000,
                    ),
                    TidePredictionRecord(
                        stationId = "TEST1",
                        date = "2024-03-21",
                        stationType = "R",
                        predictions = listOf(CachedPrediction(now, 6.0)),
                        lastUpdated = now,
                        ttl = now + 7 * 24 * 60 * 60 * 1000,
                    ),
                )

            // Mock putItem to just return Unit
            every {
                mockTable.putItem(any<TidePredictionRecord>())
            } returns Unit

            cache.savePredictionsBatch(records)

            verify(exactly = records.size) {
                mockTable.putItem(any<TidePredictionRecord>())
            }
        }
}
