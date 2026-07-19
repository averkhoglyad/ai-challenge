package io.averkhogliad.ai.challenge.week3.weather.core.repository

import org.jetbrains.exposed.v1.core.Table

object GeoCacheTable : Table("geo_cache") {
    val cacheKey = varchar("cache_key", 255)
    val name = varchar("name", 255)
    val country = varchar("country", 255)
    val countryCode = varchar("country_code", 10)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val geonameId = long("geoname_id")
    val admin1 = varchar("admin1", 255).nullable()
    val population = integer("population").nullable()
    val alternativesCount = integer("alternatives_count")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(cacheKey)
}
