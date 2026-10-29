package com.example.soundboard.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An update must open every older database without losing data. These tests build databases in
 * each old format on a real SQLite, run the app's own migration statements, and check that the
 * result is exactly the schema Room expects (as exported to app/schemas) with the buttons intact.
 */
class MigrationChainTest {

    /** Schemas from before Room exported them (version 1 and 2), as their tables were created. */
    private val handWrittenSchemas = mapOf(
        1 to listOf(
            "CREATE TABLE IF NOT EXISTS `buttons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`colorId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        ),
        2 to listOf(
            "CREATE TABLE IF NOT EXISTS `buttons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`colorId` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `fxId` TEXT NOT NULL DEFAULT 'none', " +
                "PRIMARY KEY(`id`))",
        ),
    )

    private val schemaDir = File("schemas/${SoundboardDatabase::class.java.name}")

    /** The CREATE statements (tables and indices) of an exported schema version. */
    private fun exportedSchema(version: Int): List<String>? {
        val file = File(schemaDir, "$version.json")
        if (!file.exists()) return null
        val json = file.readText()
        return json.split("\"tableName\": \"").drop(1).flatMap { chunk ->
            val table = chunk.substringBefore('"')
            CREATE_SQL.findAll(chunk).map { match ->
                match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\").replace("\${TABLE_NAME}", table)
            }.toList()
        }
    }

    private fun schema(version: Int): List<String> =
        handWrittenSchemas[version] ?: exportedSchema(version) ?: error("No schema for version $version")

    private fun database(statements: List<String>): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").also { db ->
            db.createStatement().use { st -> statements.forEach(st::execute) }
        }

    private fun migrate(db: Connection, from: Int) {
        db.createStatement().use { st ->
            SoundboardDatabase.MIGRATION_STEPS.filter { it.from >= from }.forEach { step -> step.statements.forEach(st::execute) }
        }
    }

    /** Tables with their columns and indices, the parts Room checks when it opens a database. */
    private fun shape(db: Connection): Map<String, Set<String>> {
        val tables = db.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        return tables.associateWith { table ->
            val columns = db.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                    generateSequence {
                        if (!rs.next()) return@generateSequence null
                        "column ${rs.getString("name")} ${rs.getString("type")} notnull=${rs.getInt("notnull")} " +
                            "default=${rs.getString("dflt_value")} pk=${rs.getInt("pk")}"
                    }.toSet()
                }
            }
            val indices = db.createStatement().use { st ->
                st.executeQuery("PRAGMA index_list(`$table`)").use { rs ->
                    generateSequence {
                        if (!rs.next()) return@generateSequence null
                        "index ${rs.getString("name")} unique=${rs.getInt("unique")}"
                    }.filterNot { it.contains("sqlite_autoindex") }.toSet()
                }
            }
            columns + indices
        }
    }

    @Test
    fun migrationsLeadFromTheFirstVersionToTheCurrentOneStepByStep() {
        val steps = SoundboardDatabase.MIGRATION_STEPS.map { it.from to it.to }
        assertEquals((1 until SoundboardDatabase.VERSION).map { it to it + 1 }, steps)
    }

    @Test
    fun theCurrentSchemaIsExported() {
        assertTrue(
            "Build once so Room exports ${SoundboardDatabase.VERSION}.json, then commit it",
            File(schemaDir, "${SoundboardDatabase.VERSION}.json").exists(),
        )
    }

    @Test
    fun everyOldVersionUpgradesToExactlyTheCurrentSchema() {
        val expected = database(schema(SoundboardDatabase.VERSION)).use(::shape)
        for (version in 1 until SoundboardDatabase.VERSION) {
            val upgraded = database(schema(version)).use { db ->
                migrate(db, from = version)
                shape(db)
            }
            assertEquals("upgrade from version $version", expected, upgraded)
        }
    }

    @Test
    fun buttonsFromVersionOneSurviveEveryUpgrade() {
        database(schema(1)).use { db ->
            db.createStatement().use { st ->
                st.execute("INSERT INTO buttons (id, name, colorId, sortOrder) VALUES ('a', 'Hello', 'blue', 1)")
                st.execute("INSERT INTO buttons (id, name, colorId, sortOrder) VALUES ('b', 'Bye', 'rose', 2)")
            }
            migrate(db, from = 1)
            db.createStatement().use { st ->
                st.executeQuery("SELECT * FROM buttons ORDER BY sortOrder").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("a", rs.getString("id"))
                    assertEquals("Hello", rs.getString("name"))
                    assertEquals("blue", rs.getString("colorId"))
                    assertEquals("none", rs.getString("fxId"))
                    assertNull(rs.getString("melodyId"))
                    assertTrue(rs.next())
                    assertEquals("Bye", rs.getString("name"))
                    assertTrue(!rs.next())
                }
            }
        }
    }

    @Test
    fun buttonsWithEffectsFromVersionTwoSurvive() {
        database(schema(2)).use { db ->
            db.createStatement().use { st ->
                st.execute("INSERT INTO buttons (id, name, colorId, sortOrder, fxId) VALUES ('a', 'Squeak', 'blue', 1, 'mouseyfier')")
            }
            migrate(db, from = 2)
            db.createStatement().use { st ->
                st.executeQuery("SELECT name, fxId, melodyId FROM buttons").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Squeak", rs.getString("name"))
                    assertEquals("mouseyfier", rs.getString("fxId"))
                    assertNull(rs.getString("melodyId"))
                }
                st.executeQuery("SELECT COUNT(*) FROM melodies").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(0, rs.getInt(1))
                }
            }
        }
    }

    private companion object {
        val CREATE_SQL = Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"")
    }
}
