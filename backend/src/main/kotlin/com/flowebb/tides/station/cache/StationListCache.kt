package com.flowebb.tides.station.cache

import com.flowebb.tides.station.NoaaStationMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import mu.KotlinLogging
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.io.InputStreamReader
import kotlin.io.readText
import kotlinx.serialization.Serializable

class StationListCache(
    private val isLocalDevelopment: Boolean = System.getenv("AWS_SAM_LOCAL") == "true",
    private val s3Client: S3Client? = if (!isLocalDevelopment) {
        S3Client.builder()
            .httpClient(UrlConnectionHttpClient.builder().build())
            .build()
    } else null,
    private val bucketName: String = System.getenv("STATION_LIST_BUCKET") ?: "tides-station-cache",
    baseCacheDir: String? = null
) {
    private val logger = KotlinLogging.logger {}
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val cacheKey = "station-list.json"
    private val metadataKey = "station-list-metadata.json"

    private val localCacheDir: Path = when {
        // If base directory is provided, use it
        baseCacheDir != null -> Paths.get(baseCacheDir)
        // In Lambda environment, use temp directory
        !isLocalDevelopment -> Files.createTempDirectory("tides-cache")
        // Local development, use project directory
        else -> Files.createTempDirectory("tides-cache")
    }

    init {
        if (isLocalDevelopment) {
            initializeLocalCache()
            logger.info { "Cache directory is: $localCacheDir" }
        }
    }

    private fun initializeLocalCache() {
        try {
            if (!Files.exists(localCacheDir)) {
                logger.info { "Creating local cache directory: $localCacheDir" }
                Files.createDirectories(localCacheDir)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error initializing local cache directory: $localCacheDir" }
            // Don't throw here, let individual operations handle failures
        }
    }

    private fun ensureCacheDirectoryExists() {
        if (!Files.exists(localCacheDir)) {
            try {
                Files.createDirectories(localCacheDir)
            } catch (e: Exception) {
                logger.error(e) { "Failed to create cache directory: $localCacheDir" }
                throw IOException("Failed to create cache directory: $localCacheDir", e)
            }
        }
    }

    private fun getLocalStationList(): List<NoaaStationMetadata>? {
        val cacheFile = localCacheDir.resolve(cacheKey).toFile()
        return if (Files.exists(cacheFile.toPath())) {
            try {
                json.decodeFromString<List<NoaaStationMetadata>>(cacheFile.readText())
            } catch (e: Exception) {
                logger.error(e) { "Failed to read local station list cache" }
                null
            }
        } else null
    }

    private fun saveLocalStationList(stations: List<NoaaStationMetadata>) {
        try {
            ensureCacheDirectoryExists()

            val stationFile = localCacheDir.resolve(cacheKey).toFile()
            stationFile.writeText(json.encodeToString(stations))

            // Save metadata
            val metadata = CacheMetadata(
                lastUpdated = Instant.now().toEpochMilli(),
                count = stations.size
            )
            val metadataFile = localCacheDir.resolve(metadataKey).toFile()
            metadataFile.writeText(json.encodeToString(metadata))

            logger.info { "Saved ${stations.size} stations to local cache at $localCacheDir" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save local station list cache" }
            throw e  // Rethrow to allow caller to handle
        }
    }

    private fun getLocalMetadata(): CacheMetadata? {
        val metadataFile = localCacheDir.resolve(metadataKey).toFile()
        return if (Files.exists(metadataFile.toPath())) {
            try {
                json.decodeFromString<CacheMetadata>(metadataFile.readText())
            } catch (e: Exception) {
                logger.error(e) { "Failed to read local metadata" }
                null
            }
        } else null
    }

    @Serializable
    data class CacheMetadata(
        val lastUpdated: Long,
        val count: Int
    )

    fun getStationList(): List<NoaaStationMetadata>? {
        return if (isLocalDevelopment) {
            getLocalStationList()
        } else {
            getS3StationList()
        }
    }

    fun saveStationList(stations: List<NoaaStationMetadata>) {
        if (isLocalDevelopment) {
            saveLocalStationList(stations)
        } else {
            saveS3StationList(stations)
        }
    }

    fun isCacheValid(): Boolean {
        val metadata = if (isLocalDevelopment) {
            getLocalMetadata()
        } else {
            getS3Metadata()
        }

        return metadata?.let {
            val age = Instant.now().toEpochMilli() - it.lastUpdated
            age < 24 * 60 * 60 * 1000 // 24 hours
        } ?: false
    }

    private fun getS3StationList(): List<NoaaStationMetadata>? {
        return try {
            val request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(cacheKey)
                .build()

            s3Client?.getObject(request)?.let { response: ResponseInputStream<GetObjectResponse> ->
                InputStreamReader(response).use { reader ->
                    json.decodeFromString(reader.readText())
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to read S3 station list cache" }
            null
        }
    }

    private fun saveS3StationList(stations: List<NoaaStationMetadata>) {
        try {
            val content = json.encodeToString(stations)
            val request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(cacheKey)
                .build()

            s3Client?.putObject(request, RequestBody.fromString(content))

            // Save metadata
            val metadata = CacheMetadata(
                lastUpdated = Instant.now().toEpochMilli(),
                count = stations.size
            )
            val metadataRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .build()

            s3Client?.putObject(metadataRequest, RequestBody.fromString(json.encodeToString(metadata)))

            logger.info { "Saved ${stations.size} stations to S3 cache" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save S3 station list cache" }
        }
    }

    private fun getS3Metadata(): CacheMetadata? {
        return try {
            val request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .build()

            s3Client?.getObject(request)?.let { response: ResponseInputStream<GetObjectResponse> ->
                InputStreamReader(response).use { reader ->
                    json.decodeFromString(reader.readText())
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to read S3 metadata" }
            null
        }
    }
}
