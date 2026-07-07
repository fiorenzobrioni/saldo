package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.TagEntity
import com.callbackdev.saldo.core.domain.model.Tag

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name)

fun Tag.toEntity(): TagEntity = TagEntity(id = id, name = name)
