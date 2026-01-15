package com.example.newsreader.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val summary: String,
    val fullContent: String? = null,
    val source: String,
    val imageUrl: String,
    val url: String,
    val publishedDate: Long,
    val addedDate: Long,
    val isRead: Boolean = false
)

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedDate DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles ORDER BY publishedDate DESC LIMIT 50")
    suspend fun getAllArticlesList(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    suspend fun getArticleById(articleId: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE publishedDate > :currentDate ORDER BY publishedDate ASC LIMIT 1")
    suspend fun getNextArticle(currentDate: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE publishedDate < :currentDate ORDER BY publishedDate DESC LIMIT 1")
    suspend fun getPreviousArticle(currentDate: Long): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :articleId")
    suspend fun updateReadStatus(articleId: String, isRead: Boolean)

    @Query("UPDATE articles SET fullContent = :fullContent WHERE id = :articleId")
    suspend fun updateFullContent(articleId: String, fullContent: String)

    @Query("DELETE FROM articles")
    suspend fun deleteAllArticles()
}

@Database(entities = [ArticleEntity::class], version = 3, exportSchema = false)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var INSTANCE: NewsDatabase? = null

        fun getDatabase(context: Context): NewsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NewsDatabase::class.java,
                    "news_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ArticleRepository(private val articleDao: ArticleDao) {
    val allArticles: Flow<List<ArticleEntity>> = articleDao.getAllArticles()

    suspend fun getAllArticlesSync(): List<ArticleEntity> {
        return articleDao.getAllArticlesList()
    }

    suspend fun getArticleById(articleId: String): ArticleEntity? {
        return articleDao.getArticleById(articleId)
    }

    suspend fun getNextArticle(currentArticleId: String): ArticleEntity? {
        val currentArticle = articleDao.getArticleById(currentArticleId)
        return currentArticle?.let {
            articleDao.getNextArticle(it.publishedDate)
        }
    }

    suspend fun getPreviousArticle(currentArticleId: String): ArticleEntity? {
        val currentArticle = articleDao.getArticleById(currentArticleId)
        return currentArticle?.let {
            articleDao.getPreviousArticle(it.publishedDate)
        }
    }

    suspend fun insertArticles(articles: List<ArticleEntity>) {
        articleDao.insertArticles(articles)
    }

    suspend fun markAsRead(articleId: String) {
        articleDao.updateReadStatus(articleId, true)
    }

    suspend fun updateFullContent(articleId: String, fullContent: String) {
        articleDao.updateFullContent(articleId, fullContent)
    }

    suspend fun deleteAllArticles() {
        articleDao.deleteAllArticles()
    }
}