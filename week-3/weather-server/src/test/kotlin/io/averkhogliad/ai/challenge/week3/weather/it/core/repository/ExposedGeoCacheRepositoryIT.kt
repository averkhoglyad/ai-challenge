package io.averkhogliad.ai.challenge.week3.weather.it.core.repository

import io.averkhogliad.ai.challenge.week3.weather.core.model.GeoCacheEntry
import io.averkhogliad.ai.challenge.week3.weather.core.repository.ExposedGeoCacheRepository
import io.averkhogliad.ai.challenge.week3.weather.core.repository.GeoCacheTable
import io.averkhogliad.ai.challenge.week3.weather.infra.config.StringToDurationConverter
import io.averkhogliad.ai.challenge.week3.weather.infra.config.WeatherDurationProperties
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.core.convert.support.DefaultConversionService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class ExposedGeoCacheRepositoryIT : FreeSpec() {

    lateinit var repository: ExposedGeoCacheRepository
    lateinit var testDatabase: Database
    var dbFile: Path? = null

    override suspend fun beforeTest(testCase: io.kotest.core.test.TestCase) {
        dbFile = Files.createTempFile("weather-geo-cache-", ".sqlite")
        testDatabase = Database.connect("jdbc:sqlite:${dbFile!!.toAbsolutePath()}", "org.sqlite.JDBC")
        transaction(testDatabase) {
            SchemaUtils.create(GeoCacheTable)
            GeoCacheTable.deleteAll()
        }
        val conversionService = DefaultConversionService().apply {
            addConverter(StringToDurationConverter())
        }
        repository = ExposedGeoCacheRepository(
            testDatabase,
            WeatherDurationProperties(
                conversionService = conversionService,
                connectTimeout = "PT3S",
                readTimeout = "PT5S",
                geoTtl = "P30D",
                currentTtl = "PT15M",
                forecastTtl = "PT3H"
            )
        )
    }

    override suspend fun afterTest(testCase: io.kotest.core.test.TestCase, result: io.kotest.core.test.TestResult) {
        dbFile?.deleteIfExists()
        dbFile = null
    }

    init {
        "GeoCacheRepository" - {
            "saves and retrieves GeoCacheEntry" {
                val entry = GeoCacheEntry(
                    name = "London",
                    country = "United Kingdom",
                    countryCode = "GB",
                    latitude = 51.51,
                    longitude = -0.13,
                    geonameId = 2643743,
                    alternativesCount = 29
                )

                repository.save("geo:london", entry)
                val result = repository.findByKey("geo:london")

                result shouldNotBe null
                result!!.name shouldBe "London"
                result.countryCode shouldBe "GB"
                result.geonameId shouldBe 2643743
                result.alternativesCount shouldBe 29
            }

            "returns null for non-existent key" {
                repository.findByKey("geo:nonexistent").shouldBeNull()
            }

            "deletes expired entries" {
                val oldEntry = GeoCacheEntry(
                    name = "OldCity", country = "OC", countryCode = "OC",
                    latitude = 0.0, longitude = 0.0, geonameId = 1
                )
                val newEntry = GeoCacheEntry(
                    name = "NewCity", country = "NC", countryCode = "NC",
                    latitude = 0.0, longitude = 0.0, geonameId = 2
                )
                repository.save("geo:old", oldEntry)
                repository.save("geo:new", newEntry)
                transaction(testDatabase) {
                    GeoCacheTable.update({ GeoCacheTable.cacheKey eq "geo:old" }) {
                        it[createdAt] = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
                    }
                }

                val oneDayMs = 24 * 60 * 60 * 1000L
                repository.deleteExpired(oneDayMs)

                repository.findByKey("geo:old").shouldBeNull()
                repository.findByKey("geo:new") shouldNotBe null
            }
        }
    }
}
