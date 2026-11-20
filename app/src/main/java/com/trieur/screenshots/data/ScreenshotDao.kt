package com.trieur.screenshots.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val count: Int)
data class AppCount(val sourceApp: String, val count: Int)
data class SourceCount(val source: String, val count: Int)
data class ClassificationInput(
    val mediaId: Long,
    val displayName: String,
    val text: String,
    val source: String,
    val folder: String?,
)
data class HashInput(val mediaId: Long, val uri: String)

/** Tous les filtres sont facultatifs : null = pas de filtre. */
@Dao
interface ScreenshotDao {

    @Query(
        """
        SELECT * FROM screenshots
        WHERE (:category IS NULL OR category = :category)
          AND (:app IS NULL OR sourceApp = :app)
          AND (:source IS NULL OR source = :source)
        ORDER BY dateTaken DESC
        """
    )
    fun filtered(category: String?, app: String?, source: String?): Flow<List<Screenshot>>

    @Query(
        """
        SELECT screenshots.* FROM screenshots
        JOIN screenshots_fts ON screenshots.mediaId = screenshots_fts.rowid
        WHERE screenshots_fts MATCH :ftsQuery
          AND (:category IS NULL OR screenshots.category = :category)
          AND (:app IS NULL OR screenshots.sourceApp = :app)
          AND (:source IS NULL OR screenshots.source = :source)
        ORDER BY screenshots.dateTaken DESC
        """
    )
    fun search(ftsQuery: String, category: String?, app: String?, source: String?): Flow<List<Screenshot>>

    @Query("SELECT COUNT(*) FROM screenshots")
    fun count(): Flow<Int>

    /** Compteurs par catégorie, limités à l'appli et à la source choisies. */
    @Query(
        """
        SELECT category, COUNT(*) AS count FROM screenshots
        WHERE (:app IS NULL OR sourceApp = :app) AND (:source IS NULL OR source = :source)
        GROUP BY category
        """
    )
    fun categoryCounts(app: String?, source: String?): Flow<List<CategoryCount>>

    @Query(
        """
        SELECT sourceApp, COUNT(*) AS count FROM screenshots
        WHERE sourceApp IS NOT NULL AND (:source IS NULL OR source = :source)
        GROUP BY sourceApp ORDER BY count DESC, sourceApp
        """
    )
    fun appCounts(source: String?): Flow<List<AppCount>>

    @Query("SELECT source, COUNT(*) AS count FROM screenshots GROUP BY source")
    fun sourceCounts(): Flow<List<SourceCount>>

    @Query("SELECT mediaId FROM screenshots")
    suspend fun indexedIds(): List<Long>

    @Query("SELECT mediaId, displayName, text, source, folder FROM screenshots")
    suspend fun classificationInputs(): List<ClassificationInput>

    /** Reclassement automatique : ne touche pas à la catégorie si elle a été choisie à la main. */
    @Query(
        """
        UPDATE screenshots SET sourceApp = :sourceApp,
          category = CASE WHEN categoryLocked THEN category ELSE :category END
        WHERE mediaId = :mediaId
        """
    )
    suspend fun updateClassification(mediaId: Long, sourceApp: String?, category: String)

    /** [locked] = true pour un choix manuel, false pour revenir au classement automatique. */
    @Query("UPDATE screenshots SET category = :category, categoryLocked = :locked WHERE mediaId = :mediaId")
    suspend fun setCategory(mediaId: Long, category: String, locked: Boolean)

    @Query("SELECT * FROM screenshots WHERE mediaId = :mediaId")
    fun observe(mediaId: Long): Flow<Screenshot?>

    @Query("SELECT mediaId, uri FROM screenshots WHERE dhash IS NULL")
    suspend fun missingHashes(): List<HashInput>

    @Query("UPDATE screenshots SET dhash = :hash WHERE mediaId = :mediaId")
    suspend fun setHash(mediaId: Long, hash: Long)

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
