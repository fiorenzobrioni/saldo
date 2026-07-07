package com.callbackdev.saldo.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Many-to-many link between movements and tags. */
@Entity(
    tableName = "transaction_tag_cross_ref",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class TransactionTagCrossRef(
    val transactionId: Long,
    val tagId: Long,
)
