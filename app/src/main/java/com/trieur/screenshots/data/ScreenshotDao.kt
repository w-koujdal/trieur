package com.trieur.screenshots.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM screenshots ORDER BY dateTaken DESC")
    fun all(): Flow<List<Screenshot>>

    @Query(
        """
        SELECT screenshots.* FROM screenshots
        JOIN screenshots_fts ON screenshots.mediaId = screenshots_fts.rowid
        WHERE screenshots_fts MATCH :ftsQuery
        ORDER BY screenshots.dateTaken DESC
        """
    )
    fun search(ftsQuery: String): Flow<List<Screenshot>>

    @Query("SELECT COUNT(*) FROM screenshots")
    fun count(): Flow<Int>

    @Query("SELECT mediaId FROM screenshots")
    suspend fun indexedIds(): List<Long>

    // IGNORE plutôt que REPLACE : REPLACE ne déclencherait pas le trigger de suppression FTS.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(screenshot: Screenshot)

    @Query("DELETE FROM screenshots WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

/**
 * Transforme la saisie utilisateur en requête FTS sûre : chaque mot devient un préfixe,
 * tous les mots doivent être présents. Renvoie null si rien d'exploitable.
 */
fun toFtsQuery(input: String): String? {
    val tokens = searchTokens(input)
    if (tokens.isEmpty()) return null
    // Minuscules : l'index ignore déjà la casse, et ça évite que OR / AND / NOT / NEAR
    // tapés en majuscules soient interprétés comme des opérateurs FTS.
    return tokens.joinToString(" ") { "${it.lowercase()}*" }
}
