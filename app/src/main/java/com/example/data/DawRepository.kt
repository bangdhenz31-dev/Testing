package com.example.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class DawRepository(private val dao: DawDao) {
    constructor(context: Context) : this(DawDatabase.getInstance(context).dawDao())

    fun observeProjects(): Flow<List<DawProject>> {
        return dao.observeProjects().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeProjectNotes(projectId: String): Flow<List<ProjectNote>> {
        require(projectId.isNotBlank()) { "Project ID tidak boleh kosong." }
        return dao.observeNotesForProject(projectId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun createProject(
        title: String,
        template: DawProject = DawProject(),
    ): Result<DawProject> {
        return try {
            val now = System.currentTimeMillis()
            val cleanTitle = title.trim().ifBlank { "Proyek 8D Baru" }.take(80)
            val project = sanitizeProject(
                template.copy(
                    id = UUID.randomUUID().toString(),
                    title = cleanTitle,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
            dao.upsertProject(project.toEntity())
            Result.success(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProjectState(project: DawProject): Result<Unit> {
        return try {
            require(project.id.isNotBlank()) { "Project ID wajib diisi untuk menyimpan." }
            val now = System.currentTimeMillis()
            val cleaned = sanitizeProject(
                project.copy(
                    createdAtMillis = if (project.createdAtMillis > 0L) project.createdAtMillis else now,
                    updatedAtMillis = now,
                )
            )
            dao.upsertProject(cleaned.toEntity())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addProjectNote(
        project: DawProject,
        message: String,
        barPosition: Long = 1L,
    ): Result<Unit> {
        return try {
            require(project.id.isNotBlank()) { "Simpan proyek terlebih dahulu." }
            val cleanMsg = message.trim().take(400)
            require(cleanMsg.isNotEmpty()) { "Catatan tidak boleh kosong." }
            val note = ProjectNoteEntity(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                message = cleanMsg,
                barPosition = barPosition.coerceIn(1L, 128L),
                createdAtMillis = System.currentTimeMillis(),
            )
            dao.insertNote(note)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        return try {
            require(noteId.isNotBlank())
            dao.deleteNoteById(noteId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProject(project: DawProject): Result<Unit> {
        return try {
            require(project.id.isNotBlank())
            dao.deleteProjectById(project.id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeProject(project: DawProject): DawProject {
        val cleanEq = project.eqBandsDb.take(5).let { list ->
            if (list.size == 5) list.map { it.coerceIn(-12.0, 12.0) } else listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val cleanSteps = project.stepPattern.take(16).let { list ->
            if (list.size == 16) list else List(16) { 0L }
        }
        return project.copy(
            title = project.title.trim().ifBlank { "Proyek 8D" }.take(80),
            bpm = project.bpm.coerceIn(40L, 240L),
            spatialPattern = SpatialPattern.fromRaw(project.spatialPattern).name,
            orbitSpeedHz = project.orbitSpeedHz.coerceIn(0.05, 2.0),
            orbitRadiusMeters = project.orbitRadiusMeters.coerceIn(1.0, 10.0),
            reverbMix = project.reverbMix.coerceIn(0.0, 1.0),
            bassBoostDb = project.bassBoostDb.coerceIn(0.0, 18.0),
            eqBandsDb = cleanEq,
            compressorThresholdDb = project.compressorThresholdDb.coerceIn(-40.0, 0.0),
            delayMix = project.delayMix.coerceIn(0.0, 1.0),
            latencyFrames = project.latencyFrames.coerceIn(64L, 2048L),
            youtubeVideoId = project.youtubeVideoId.take(32),
            youtubeTitle = project.youtubeTitle.take(140),
            synthGain = project.synthGain.coerceIn(0.0, 1.5),
            clipGain = project.clipGain.coerceIn(0.0, 1.5),
            micGain = project.micGain.coerceIn(0.0, 2.0),
            youtubeGain = project.youtubeGain.coerceIn(0.0, 1.5),
            masterGain = project.masterGain.coerceIn(0.0, 1.5),
            stepPattern = cleanSteps,
        )
    }

    private fun DawProjectEntity.toModel(): DawProject = DawProject(
        id = id,
        title = title,
        bpm = bpm,
        spatialPattern = spatialPattern,
        orbitSpeedHz = orbitSpeedHz,
        orbitRadiusMeters = orbitRadiusMeters,
        reverbMix = reverbMix,
        bassBoostDb = bassBoostDb,
        eqBandsDb = eqBandsDb,
        compressorEnabled = compressorEnabled,
        compressorThresholdDb = compressorThresholdDb,
        delayEnabled = delayEnabled,
        delayMix = delayMix,
        limiterEnabled = limiterEnabled,
        latencyFrames = latencyFrames,
        youtubeVideoId = youtubeVideoId,
        youtubeTitle = youtubeTitle,
        synthGain = synthGain,
        clipGain = clipGain,
        micGain = micGain,
        youtubeGain = youtubeGain,
        masterGain = masterGain,
        stepPattern = stepPattern,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun DawProject.toEntity(): DawProjectEntity = DawProjectEntity(
        id = id,
        title = title,
        bpm = bpm,
        spatialPattern = spatialPattern,
        orbitSpeedHz = orbitSpeedHz,
        orbitRadiusMeters = orbitRadiusMeters,
        reverbMix = reverbMix,
        bassBoostDb = bassBoostDb,
        eqBandsDb = eqBandsDb,
        compressorEnabled = compressorEnabled,
        compressorThresholdDb = compressorThresholdDb,
        delayEnabled = delayEnabled,
        delayMix = delayMix,
        limiterEnabled = limiterEnabled,
        latencyFrames = latencyFrames,
        youtubeVideoId = youtubeVideoId,
        youtubeTitle = youtubeTitle,
        synthGain = synthGain,
        clipGain = clipGain,
        micGain = micGain,
        youtubeGain = youtubeGain,
        masterGain = masterGain,
        stepPattern = stepPattern,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun ProjectNoteEntity.toModel(): ProjectNote = ProjectNote(
        id = id,
        projectId = projectId,
        message = message,
        barPosition = barPosition,
        createdAtMillis = createdAtMillis,
    )
}
