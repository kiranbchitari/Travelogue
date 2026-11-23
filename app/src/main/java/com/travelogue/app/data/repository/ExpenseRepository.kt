package com.travelogue.app.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import androidx.room.withTransaction
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val db: AppDatabase, private val media: MediaStorage) {
    private val dao = db.expenseDao()

    fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()
    fun observeForTrip(tripId: Long): Flow<List<ExpenseEntity>> = dao.observeForTrip(tripId)
    fun observe(id: Long): Flow<ExpenseEntity?> = dao.observe(id)
    fun observeSplitsForTrip(tripId: Long): Flow<List<ExpenseSplitEntity>> = dao.observeSplitsForTrip(tripId)
    fun observeSettlements(tripId: Long): Flow<List<SettlementEntity>> = dao.observeSettlements(tripId)
    fun observeCategories(): Flow<List<ExpenseCategoryEntity>> = dao.observeCategories()

    suspend fun get(id: Long): ExpenseEntity? = dao.get(id)
    suspend fun getSplits(expenseId: Long): List<ExpenseSplitEntity> = dao.getSplits(expenseId)
    suspend fun getCategories(): List<ExpenseCategoryEntity> = dao.getCategories()

    /**
     * Saves an expense with its split rows atomically.
     * @param shares travellerId -> amount owed (in the expense currency). Ignored when splitType is NONE.
     */
    suspend fun save(
        expense: ExpenseEntity,
        shares: Map<Long, Double>,
        newReceipt: Uri? = null,
        removeReceipt: Boolean = false,
    ): Long {
        val trip = db.tripDao().get(expense.tripId) ?: error("Trip not found")
        val previous = if (expense.id != 0L) dao.get(expense.id) else null
        var receipt = previous?.receiptPath ?: expense.receiptPath
        var toDelete: String? = null
        var imported: String? = null
        if (newReceipt != null) {
            imported = media.importImage(newReceipt, trip.uuid, "receipts").relativePath
            receipt = imported
            toDelete = previous?.receiptPath
        } else if (removeReceipt) {
            toDelete = receipt
            receipt = null
        }
        val id = media.recordOrDiscard(imported) { db.withTransaction {
            val row = expense.copy(receiptPath = receipt)
            val id = if (expense.id == 0L) dao.insert(row) else { dao.update(row); expense.id }
            dao.clearSplits(id)
            if (expense.splitType != SplitType.NONE) {
                dao.insertSplits(shares.filter { it.value > 0.0 }.map { ExpenseSplitEntity(expenseId = id, travellerId = it.key, amount = it.value) })
            }
            id
        } }
        toDelete?.let { media.delete(it) }
        return id
    }

    suspend fun delete(id: Long) {
        val e = dao.get(id) ?: return
        dao.delete(id)
        media.delete(e.receiptPath)
    }

    suspend fun addSettlement(s: SettlementEntity): Long = dao.insertSettlement(s)
    suspend fun deleteSettlement(id: Long) = dao.deleteSettlement(id)

    // Categories --------------------------------------------------------------------------------

    /** App start-up, loading sample trips and clearing data can all get here at once; only one may insert. */
    private val defaultCategoriesLock = Mutex()

    suspend fun ensureDefaultCategories() = defaultCategoriesLock.withLock {
        if (dao.categoryCount() == 0) dao.insertCategories(DefaultCategories.all)
    }

    suspend fun addCategory(name: String, iconKey: String, color: Long): Long? {
        val n = name.trim()
        if (n.isEmpty() || dao.findCategory(n) != null) return null
        val order = (dao.getCategories().maxOfOrNull { it.sortOrder } ?: 0) + 1
        return dao.insertCategory(ExpenseCategoryEntity(name = n, iconKey = iconKey, color = color, sortOrder = order))
    }

    suspend fun updateCategory(c: ExpenseCategoryEntity) = dao.updateCategory(c)

    /** Expenses in a deleted category fall back to "uncategorised" (FK is SET NULL). */
    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
}

object DefaultCategories {
    val all = listOf(
        ExpenseCategoryEntity(name = "Transportation", iconKey = "transport", color = 0xFF2F5D62, isDefault = true, sortOrder = 0),
        ExpenseCategoryEntity(name = "Hotel / Accommodation", iconKey = "hotel", color = 0xFF7A4E6B, isDefault = true, sortOrder = 1),
        ExpenseCategoryEntity(name = "Food", iconKey = "food", color = 0xFFC5603C, isDefault = true, sortOrder = 2),
        ExpenseCategoryEntity(name = "Restaurants", iconKey = "restaurant", color = 0xFFD98B84, isDefault = true, sortOrder = 3),
        ExpenseCategoryEntity(name = "Activities", iconKey = "activity", color = 0xFF7D8F5E, isDefault = true, sortOrder = 4),
        ExpenseCategoryEntity(name = "Shopping", iconKey = "shopping", color = 0xFFC99A2E, isDefault = true, sortOrder = 5),
        ExpenseCategoryEntity(name = "Tickets", iconKey = "ticket", color = 0xFF52687D, isDefault = true, sortOrder = 6),
        ExpenseCategoryEntity(name = "Fuel", iconKey = "fuel", color = 0xFF8C6A4F, isDefault = true, sortOrder = 7),
        ExpenseCategoryEntity(name = "Parking", iconKey = "parking", color = 0xFF4E8A86, isDefault = true, sortOrder = 8),
        ExpenseCategoryEntity(name = "Medical", iconKey = "medical", color = 0xFFB3412E, isDefault = true, sortOrder = 9),
        ExpenseCategoryEntity(name = "Other", iconKey = "other", color = 0xFFA39A90, isDefault = true, sortOrder = 10),
    )
}
