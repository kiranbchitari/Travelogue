@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, LocalDateTimeSerializer::class)

package com.travelogue.app.data.db.entity

import com.travelogue.app.data.db.LocalDateSerializer
import com.travelogue.app.data.db.LocalDateTimeSerializer
import com.travelogue.app.data.db.LocalTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "expense_categories", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Key into the icon registry in the design system (see CategoryIcons). */
    val iconKey: String = "other",
    /** ARGB color. */
    val color: Long = 0xFFA3A3A3,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExpenseCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TravellerEntity::class, parentColumns = ["id"], childColumns = ["paidByTravellerId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tripId"), Index("categoryId"), Index("paidByTravellerId"), Index("date")],
)
@Serializable
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val amount: Double,
    val currency: String,
    val categoryId: Long?,
    val date: LocalDate,
    val time: LocalTime? = null,
    val location: String = "",
    val paidByTravellerId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val description: String = "",
    val receiptPath: String? = null,
    val notes: String = "",
    val splitType: SplitType = SplitType.NONE,
    val createdAt: Long = System.currentTimeMillis(),
)

/** The share of an expense owed by one traveller (in the expense's currency). */
@Entity(
    tableName = "expense_splits",
    foreignKeys = [
        ForeignKey(entity = ExpenseEntity::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TravellerEntity::class, parentColumns = ["id"], childColumns = ["travellerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("expenseId"), Index("travellerId")],
)
@Serializable
data class ExpenseSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val travellerId: Long,
    val amount: Double,
)

/** A recorded payment from one traveller to another that settles balances. */
@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TravellerEntity::class, parentColumns = ["id"], childColumns = ["fromTravellerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TravellerEntity::class, parentColumns = ["id"], childColumns = ["toTravellerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId"), Index("fromTravellerId"), Index("toTravellerId")],
)
@Serializable
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val fromTravellerId: Long,
    val toTravellerId: Long,
    /** In trip currency. */
    val amount: Double,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
