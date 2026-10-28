/*
 * LastWave-compatible HTTP response shapes, derived from AddonClient.kt in
 * Clash-Projects/LastWave-Native (GPL-3.0), commit 24d2bf52c9c55f9067c273ce89a40e91a69ef433.
 */
package com.music.bitchord.data.sources.addon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

internal object LastWaveResponses {
    private val listKeys = listOf("tracks", "data", "results", "items")

    /** LastWave accepts a bare list as well as tracks/data/results/items wrappers. */
    fun search(json: Json, body: String): AddonSearchResponse {
        val root = json.parseToJsonElement(body)
        val tracks = when (root) {
            is JsonArray -> root
            is JsonObject -> listKeys.firstNotNullOfOrNull { root[it] as? JsonArray }
                ?: (root["data"] as? JsonObject)?.let { data ->
                    listKeys.firstNotNullOfOrNull { data[it] as? JsonArray }
                }
            else -> null
        } ?: throw AddonException("Search response did not contain a track list")
        return AddonSearchResponse(json.decodeFromJsonElement<List<AddonTrack>>(tracks))
    }

    /** LastWave accepts a stream in the root, or inside `stream` or `data`. */
    fun stream(json: Json, body: String): AddonStream {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw AddonException("Stream response was not an object")
        val data = (root["stream"] as? JsonObject) ?: (root["data"] as? JsonObject) ?: root
        // LastWave tolerates common string/numeric and snake_case spellings.
        // Normalize only these fields; keep all other upstream decoding rules.
        val normalized = data.toMutableMap()
        copyNumber(normalized, "sampleRate", listOf("sampleRate", "sample_rate", "clockRate", "clock_rate", "samplingRate", "sampling_rate"))
        copyNumber(normalized, "bitDepth", listOf("bitDepth", "bit_depth", "bitType", "bit_type", "bitsPerSample", "bits_per_sample"))
        copyNumber(normalized, "bitrate", listOf("bitrate"))
        return json.decodeFromJsonElement(AddonStream.serializer(), JsonObject(normalized))
    }

    private fun copyNumber(values: MutableMap<String, JsonElement>, canonical: String, aliases: List<String>) {
        val value = aliases.firstNotNullOfOrNull { name ->
            (values[name] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()
        }
        // An unparseable optional value should not make an otherwise usable
        // stream fail to decode. Absence keeps the model's normal fallback.
        if (value == null || !value.isFinite()) values.remove(canonical)
        else values[canonical] = JsonPrimitive(value)
    }
}
