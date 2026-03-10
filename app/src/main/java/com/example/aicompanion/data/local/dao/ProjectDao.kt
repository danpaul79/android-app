package com.example.aicompanion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.aicompanion.data.local.entity.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Query("SELECT * FROM projects WHERE isArchived = 0 AND isTrashed = 0 ORDER BY sortOrder, name")
    fun getAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE isTrashed = 1 ORDER BY name")
    fun getTrashed(): Flow<List<Project>>

    @Query("SELECT * FROM projects ORDER BY sortOrder, name")
    fun getAllIncludingArchived(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getById(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getByIdSync(id: Long): Project?

    @Query("SELECT name FROM projects WHERE isArchived = 0 AND isTrashed = 0 ORDER BY name")
    suspend fun getAllProjectNames(): List<String>

    @Query("UPDATE projects SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE projects SET isTrashed = 1 WHERE id = :id")
    suspend fun trashById(id: Long)

    @Query("UPDATE projects SET isTrashed = 0 WHERE id = :id")
    suspend fun restoreById(id: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM projects WHERE isTrashed = 0 ORDER BY sortOrder, name")
    suspend fun getAllNonTrashed(): List<Project>

    @Query("SELECT id, name FROM projects WHERE isArchived = 0 AND isTrashed = 0 ORDER BY name")
    suspend fun getAllProjectNamesWithIds(): List<ProjectNameId>

    // --- Sync queries ---

    @Query("SELECT * FROM projects WHERE syncVersion > :sinceVersion")
    suspend fun getDirtyProjects(sinceVersion: Long): List<Project>

    @Query("SELECT * FROM projects WHERE googleTaskListId = :googleTaskListId LIMIT 1")
    suspend fun getByGoogleTaskListId(googleTaskListId: String): Project?

    @Query("UPDATE projects SET googleTaskListId = :googleTaskListId WHERE id = :id")
    suspend fun updateGoogleTaskListId(id: Long, googleTaskListId: String)

    @Query("UPDATE projects SET syncVersion = :syncVersion WHERE id = :id")
    suspend fun updateSyncVersion(id: Long, syncVersion: Long)

    @Query("SELECT * FROM projects WHERE googleTaskListId IS NOT NULL AND isTrashed = 0")
    suspend fun getSyncedProjects(): List<Project>
}

data class ProjectNameId(val id: Long, val name: String)
