package com.travelogue.app.data.db.dao

import androidx.room.*
import com.travelogue.app.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, time DESC, id DESC") fun observeAll(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, time DESC, id DESC") fun observeForTrip(tripId: Long): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses WHERE tripId = :tripId") suspend fun getForTrip(tripId: Long): List<ExpenseEntity>
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun get(id: Long): ExpenseEntity?
    @Query("SELECT * FROM expenses WHERE id = :id") fun observe(id: Long): Flow<ExpenseEntity?>
    @Insert suspend fun insert(e: ExpenseEntity): Long
    @Update suspend fun update(e: ExpenseEntity)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT receiptPath FROM expenses WHERE receiptPath IS NOT NULL") suspend fun allReceiptPaths(): List<String>

    // Splits
    @Query("SELECT s.* FROM expense_splits s INNER JOIN expenses e ON e.id = s.expenseId WHERE e.tripId = :tripId") fun observeSplitsForTrip(tripId: Long): Flow<List<ExpenseSplitEntity>>
    @Query("SELECT s.* FROM expense_splits s INNER JOIN expenses e ON e.id = s.expenseId WHERE e.tripId = :tripId") suspend fun getSplitsForTrip(tripId: Long): List<ExpenseSplitEntity>
    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId") suspend fun getSplits(expenseId: Long): List<ExpenseSplitEntity>
    @Insert suspend fun insertSplits(splits: List<ExpenseSplitEntity>)
    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId") suspend fun clearSplits(expenseId: Long)

    // Settlements
    @Query("SELECT * FROM settlements WHERE tripId = :tripId ORDER BY date DESC, id DESC") fun observeSettlements(tripId: Long): Flow<List<SettlementEntity>>
    @Query("SELECT * FROM settlements WHERE tripId = :tripId") suspend fun getSettlements(tripId: Long): List<SettlementEntity>
    @Insert suspend fun insertSettlement(s: SettlementEntity): Long
    @Query("DELETE FROM settlements WHERE id = :id") suspend fun deleteSettlement(id: Long)

    // Categories
    @Query("SELECT * FROM expense_categories ORDER BY sortOrder, name") fun observeCategories(): Flow<List<ExpenseCategoryEntity>>
    @Query("SELECT * FROM expense_categories ORDER BY sortOrder, name") suspend fun getCategories(): List<ExpenseCategoryEntity>
    @Query("SELECT * FROM expense_categories WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findCategory(name: String): ExpenseCategoryEntity?
    @Query("SELECT COUNT(*) FROM expense_categories") suspend fun categoryCount(): Int
    @Insert suspend fun insertCategory(c: ExpenseCategoryEntity): Long
    @Insert suspend fun insertCategories(c: List<ExpenseCategoryEntity>)
    @Update suspend fun updateCategory(c: ExpenseCategoryEntity)
    @Query("DELETE FROM expense_categories WHERE id = :id") suspend fun deleteCategory(id: Long)
}
