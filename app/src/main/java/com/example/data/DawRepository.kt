package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DawRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Silakan masuk dengan Google terlebih dahulu.")
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(8) {
            repeat(8) { append(chars.random()) }
        }
    }

    fun observeUserProjects(userId: String): Flow<List<DawProject>> = flow {
        require(userId.isNotBlank()) { "User ID tidak boleh kosong." }
        val path = "projects"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DawProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjectNotes(projectId: String, userId: String): Flow<List<ProjectNote>> = flow {
        require(projectId.isNotBlank() && userId.isNotBlank())
        val path = "projects/$projectId/notes"
        emitAll(
            db.collection("projects").document(projectId).collection("notes")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ProjectNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(
        title: String,
        template: DawProject = DawProject(),
    ): Result<DawProject> {
        var docPath = "projects"
        return try {
            val uid = requireUserId()
            val ownerName = (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Produser")
                .take(60)
            val cleanTitle = title.trim().ifBlank { "Proyek 8D Baru" }.take(80)
            val inviteCode = generateInviteCode()
            val projectRef = db.collection("projects").document()
            docPath = projectRef.path

            val cleanEq = template.eqBandsDb.take(5).let { list ->
                if (list.size == 5) list.map { it.coerceIn(-12.0, 12.0) } else listOf(0.0, 0.0, 0.0, 0.0, 0.0)
            }
            val cleanSteps = template.stepPattern.take(16).let { list ->
                if (list.size == 16) list else List(16) { 0L }
            }

            val payload = mapOf(
                "ownerId" to uid,
                "ownerName" to ownerName,
                "memberUids" to listOf(uid),
                "inviteCode" to inviteCode,
                "title" to cleanTitle,
                "bpm" to template.bpm.coerceIn(40L, 240L),
                "spatialPattern" to SpatialPattern.fromRaw(template.spatialPattern).name,
                "orbitSpeedHz" to template.orbitSpeedHz.coerceIn(0.05, 2.0),
                "orbitRadiusMeters" to template.orbitRadiusMeters.coerceIn(1.0, 10.0),
                "reverbMix" to template.reverbMix.coerceIn(0.0, 1.0),
                "bassBoostDb" to template.bassBoostDb.coerceIn(0.0, 18.0),
                "eqBandsDb" to cleanEq,
                "compressorEnabled" to template.compressorEnabled,
                "compressorThresholdDb" to template.compressorThresholdDb.coerceIn(-40.0, 0.0),
                "delayEnabled" to template.delayEnabled,
                "delayMix" to template.delayMix.coerceIn(0.0, 1.0),
                "limiterEnabled" to template.limiterEnabled,
                "latencyFrames" to template.latencyFrames.coerceIn(64L, 2048L),
                "youtubeVideoId" to template.youtubeVideoId.take(32),
                "youtubeTitle" to template.youtubeTitle.take(140),
                "synthGain" to template.synthGain.coerceIn(0.0, 1.5),
                "clipGain" to template.clipGain.coerceIn(0.0, 1.5),
                "micGain" to template.micGain.coerceIn(0.0, 2.0),
                "masterGain" to template.masterGain.coerceIn(0.0, 1.5),
                "stepPattern" to cleanSteps,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            projectRef.set(payload).await()

            // Publish shareable invite lookup document
            val inviteRef = db.collection("projectInvites").document(inviteCode)
            inviteRef.set(
                mapOf(
                    "projectId" to projectRef.id,
                    "projectTitle" to cleanTitle,
                    "ownerId" to uid,
                    "ownerName" to ownerName,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Result.success(
                template.copy(
                    id = projectRef.id,
                    ownerId = uid,
                    ownerName = ownerName,
                    memberUids = listOf(uid),
                    inviteCode = inviteCode,
                    title = cleanTitle,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveProjectState(project: DawProject): Result<Unit> {
        var docPath = "projects/${project.id}"
        return try {
            requireUserId()
            require(project.id.isNotBlank()) { "Project ID wajib diisi untuk sinkronisasi." }
            val docRef = db.collection("projects").document(project.id)
            docPath = docRef.path

            val cleanEq = project.eqBandsDb.take(5).let { list ->
                if (list.size == 5) list.map { it.coerceIn(-12.0, 12.0) } else listOf(0.0, 0.0, 0.0, 0.0, 0.0)
            }
            val cleanSteps = project.stepPattern.take(16).let { list ->
                if (list.size == 16) list else List(16) { 0L }
            }

            docRef.update(
                mapOf(
                    "title" to project.title.trim().ifBlank { "Proyek 8D" }.take(80),
                    "bpm" to project.bpm.coerceIn(40L, 240L),
                    "spatialPattern" to SpatialPattern.fromRaw(project.spatialPattern).name,
                    "orbitSpeedHz" to project.orbitSpeedHz.coerceIn(0.05, 2.0),
                    "orbitRadiusMeters" to project.orbitRadiusMeters.coerceIn(1.0, 10.0),
                    "reverbMix" to project.reverbMix.coerceIn(0.0, 1.0),
                    "bassBoostDb" to project.bassBoostDb.coerceIn(0.0, 18.0),
                    "eqBandsDb" to cleanEq,
                    "compressorEnabled" to project.compressorEnabled,
                    "compressorThresholdDb" to project.compressorThresholdDb.coerceIn(-40.0, 0.0),
                    "delayEnabled" to project.delayEnabled,
                    "delayMix" to project.delayMix.coerceIn(0.0, 1.0),
                    "limiterEnabled" to project.limiterEnabled,
                    "latencyFrames" to project.latencyFrames.coerceIn(64L, 2048L),
                    "youtubeVideoId" to project.youtubeVideoId.take(32),
                    "youtubeTitle" to project.youtubeTitle.take(140),
                    "synthGain" to project.synthGain.coerceIn(0.0, 1.5),
                    "clipGain" to project.clipGain.coerceIn(0.0, 1.5),
                    "micGain" to project.micGain.coerceIn(0.0, 2.0),
                    "masterGain" to project.masterGain.coerceIn(0.0, 1.5),
                    "stepPattern" to cleanSteps,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinProjectByInviteCode(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase()
        var path = "projectInvites/$code"
        return try {
            val uid = requireUserId()
            require(code.matches(Regex("^[A-Z0-9]{6,12}$"))) {
                "Kode undangan harus 6-12 karakter alfanumerik."
            }
            val inviteSnap = db.collection("projectInvites").document(code).get().await()
            val projectId = inviteSnap.getString("projectId")
                ?: return Result.failure(IllegalArgumentException("Kode undangan tidak ditemukan."))

            path = "projects/$projectId"
            db.collection("projects").document(projectId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addProjectNote(
        project: DawProject,
        message: String,
        barPosition: Long = 1L,
    ): Result<Unit> {
        var path = "projects/${project.id}/notes"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Project ID tidak valid." }
            val cleanMsg = message.trim().take(400)
            require(cleanMsg.isNotEmpty()) { "Catatan tidak boleh kosong." }
            val authorName = (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Kolaborator")
                .take(60)
            val noteRef = db.collection("projects").document(project.id).collection("notes").document()
            path = noteRef.path

            val members = if (uid in project.memberUids) project.memberUids else (project.memberUids + uid)
            noteRef.set(
                mapOf(
                    "projectId" to project.id,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "message" to cleanMsg,
                    "barPosition" to barPosition.coerceIn(1L, 128L),
                    "memberUids" to members.take(10),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(project: DawProject): Result<Unit> {
        val path = "projects/${project.id}"
        return try {
            val uid = requireUserId()
            require(project.ownerId == uid) { "Hanya pemilik proyek yang dapat menghapus proyek." }
            if (project.inviteCode.isNotBlank()) {
                runCatching {
                    db.collection("projectInvites").document(project.inviteCode).delete().await()
                }
            }
            db.collection("projects").document(project.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
