package com.callbackdev.saldo.core.database.repository

import com.callbackdev.saldo.core.database.dao.TagDao
import com.callbackdev.saldo.core.database.mapper.toDomain
import com.callbackdev.saldo.core.database.mapper.toEntity
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomTagRepository @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeTagsForTransaction(transactionId: Long): Flow<List<Tag>> =
        tagDao.observeForTransaction(transactionId).map { rows -> rows.map { it.toDomain() } }

    override fun observeTagAssignments(): Flow<Map<Long, Set<Long>>> =
        tagDao.observeAllCrossRefs().map { refs ->
            refs.groupBy({ it.transactionId }, { it.tagId }).mapValues { (_, ids) -> ids.toSet() }
        }

    override suspend fun upsert(tag: Tag): Long {
        val entity = tag.toEntity()
        return if (entity.id == 0L) {
            tagDao.insert(entity)
        } else {
            tagDao.update(entity)
            entity.id
        }
    }

    override suspend fun delete(tag: Tag) = tagDao.delete(tag.toEntity())

    override suspend fun setTagsForTransaction(transactionId: Long, tagIds: List<Long>) =
        tagDao.setTagsForTransaction(transactionId, tagIds)
}
