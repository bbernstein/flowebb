/*
 * Created on Tue Jan 21 2025
 *
 * Copyright (c) 2025 Bernard Bernstein
 */

#include "TideService.h"
#include "WiFiService.h"

bool TideService::fetchTideData(TideData& tideData) {
    if (!WiFiService::isConnected()) {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("WiFi not connected");
        }
        return false;
    }

    if (ENABLE_DEBUG_PRINTS) {
        Serial.println("Creating secure client...");
    }
    std::unique_ptr<WiFiClientSecure> client(new WiFiClientSecure);
    if (!client) {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("Failed to create client");
        }
        return false;
    }
    
    client->setInsecure(); // Disable SSL certificate verification
    client->setTimeout(5); // Reduce timeout to 5 seconds
    
    HTTPClient http;
    http.setTimeout(5000); // 5 second timeout
    
    // Calculate time range
    time_t now = TimeService::getCurrentTime();
    time_t startTime = now - (24 * 60 * 60);     // 24 hours ago
    time_t endTime = now + (24 * 5 * 60 * 60);   // 5 days ahead
    
    String query = buildGraphQLQuery(startTime, endTime);
    if (ENABLE_DEBUG_PRINTS) {
        Serial.printf("GraphQL query: %s\n", query.c_str());
    }
    
    if (!http.begin(*client, TIDE_API_ENDPOINT)) {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("HTTP begin failed");
        }
        return false;
    }

    http.addHeader("Content-Type", "application/json");
    
    if (ENABLE_DEBUG_PRINTS) {
        Serial.println("Starting HTTP POST...");
    }
    int httpCode = http.POST(query);

    if (httpCode != HTTP_CODE_OK) {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.printf("HTTP POST failed, error: %s\n", http.errorToString(httpCode).c_str());
        }
        http.end();
        return false;
    }

    if (ENABLE_DEBUG_PRINTS) {
        Serial.println("Reading response...");
    }
    String payload = http.getString();
    http.end();

    if (payload.length() == 0) {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("Empty response received");
        }
        return false;
    }

    if (ENABLE_DEBUG_PRINTS) {
        Serial.printf("Response length: %d\n", payload.length());
        Serial.printf("Response: %s\n", payload.c_str());
        Serial.println("Parsing JSON...");
    }
    JSONVar doc = JSON.parse(payload);
    if (JSON.typeof(doc) == "undefined") {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("JSON parsing failed");
        }
        return false;
    }

    // Navigate through GraphQL response structure
    if (!doc.hasOwnProperty("data") || !doc["data"].hasOwnProperty("tides")) {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("Invalid GraphQL response structure");
        }
        return false;
    }

    JSONVar tides = doc["data"]["tides"];

    // Update current tide data
    if (ENABLE_DEBUG_PRINTS) {
        Serial.println("Updating tide data...");
    }
    String typeStr = JSON.stringify(tides["tideType"]);
    if (typeStr.length() > 2) {
        tideData.type = typeStr.substring(1, typeStr.length() - 1); // Remove quotes
    } else {
        tideData.type = "UNKNOWN";
    }
    
    tideData.currentHeight = (double)tides["waterLevel"];
    tideData.lastUpdateTime = now;

    // Process tide extremes
    if (ENABLE_DEBUG_PRINTS) {
        Serial.println("Processing extremes...");
    }
    if (tides.hasOwnProperty("extremes")) {
        JSONVar extremes = tides["extremes"];
        processTideExtremes(extremes, tideData, now);
    } else {
        if (ENABLE_DEBUG_PRINTS) {
            Serial.println("No extremes data in response");
        }
        return false;
    }

    if (ENABLE_DEBUG_PRINTS) {
        Serial.println("Tide data fetch completed successfully");
    }
    return true;
}

String TideService::buildGraphQLQuery(time_t startTime, time_t endTime) {
    char startBuff[25], endBuff[25];
    struct tm timeinfo;
    
    localtime_r(&startTime, &timeinfo);
    strftime(startBuff, sizeof(startBuff), "%Y-%m-%dT%H:%M:%S", &timeinfo);
    
    localtime_r(&endTime, &timeinfo);
    strftime(endBuff, sizeof(endBuff), "%Y-%m-%dT%H:%M:%S", &timeinfo);

    String query = "{"
                  "\"operationName\": \"GetTides\","
                  "\"variables\": {"
                  "\"stationId\": \"" + String(TIDE_STATION_ID) + "\","
                  "\"startDateTime\": \"" + String(startBuff) + "\","
                  "\"endDateTime\": \"" + String(endBuff) + "\""
                  "},"
                  "\"query\": \"query GetTides($stationId: ID!, $startDateTime: String!, $endDateTime: String!) {\\n"
                  "  tides(\\n"
                  "    stationId: $stationId\\n"
                  "    startDateTime: $startDateTime\\n"
                  "    endDateTime: $endDateTime\\n"
                  "  ) {\\n"
                  "    localTime\\n"
                  "    waterLevel\\n"
                  "    tideType\\n"
                  "    timeZoneOffsetSeconds\\n"
                  "    extremes {\\n"
                  "      type\\n"
                  "      timestamp\\n"
                  "      height\\n"
                  "    }\\n"
                  "  }\\n"
                  "}\""
                  "}";
    return query;
}

void TideService::processTideExtremes(JSONVar& extremes, TideData& tideData, time_t now) {
    // The request spans about six days (one back, five ahead), at roughly
    // four extremes a day. Extremes past this buffer are dropped. It is static
    // to keep about 1 KB off the stack during the TLS request.
    static const int MAX_PARSED_EXTREMES = 64;
    static TideExtreme parsed[MAX_PARSED_EXTREMES];

    int total = (int)extremes.length();
    if (ENABLE_DEBUG_PRINTS) {
        Serial.printf("Processing %d extremes...\n", total);
    }

    // Entries with a missing or wrongly typed field are skipped, so no NaN
    // reaches a time_t cast; parseApiExtreme also rejects unknown types.
    int count = 0;
    for (int i = 0; i < total && count < MAX_PARSED_EXTREMES; i++) {
        JSONVar entry = extremes[i];
        if (JSON.typeof(entry["timestamp"]) != "number" ||
            JSON.typeof(entry["height"]) != "number" ||
            JSON.typeof(entry["type"]) != "string") {
            continue;
        }
        String typeStr = JSON.stringify(entry["type"]);
        if (parseApiExtreme((double)entry["timestamp"], (double)entry["height"],
                            typeStr.c_str(), parsed[count])) {
            count++;
        }
    }

    tideData.numExtremes = splitTideExtremes(parsed, count, now, tideData.current,
                                             tideData.extremes, MAX_EXTREMES);

    if (ENABLE_DEBUG_PRINTS) {
        Serial.printf("Recent past extreme: %s\n", hasTimestamp(tideData.current) ? "found" : "none");
        Serial.printf("Stored %d future extremes\n", tideData.numExtremes);
    }
}
