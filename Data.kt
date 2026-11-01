package com.stokgudang.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "items", indices = [Index(value = ["code"], unique = true)])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val unit: String
)

@Entity(tableName = "batches", indices = [Index(value = ["itemId", "batchNo"], unique = true)])
data class Batch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val batchNo: String,
    val qty: Int,
    val expiry: String = ""
)

@Entity(tableName = "transactions")
data class Trx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val batchNo: String,
    val type: String, // "MASUK" atau "KELUAR"
    val qty: Int,
    val note: String,
    val time: Long
)

data class ItemRow(val id: Long, val code: String, val name: String, val unit: String, val total: Int)

@Dao
abstract class StockDao {
    @Query(
        "SELECT i.id AS id, i.code AS code, i.name AS name, i.unit AS unit, " +
            "COALESCE(SUM(b.qty), 0) AS total FROM items i " +
            "LEFT JOIN batches b ON b.itemId = i.id GROUP BY i.id ORDER BY i.name"
    )
    abstract fun itemsWithTotal(): Flow<List<ItemRow>>

    @Query("SELECT * FROM items WHERE code = :code LIMIT 1")
    abstract suspend fun findByCode(code: String): Item?

    @Insert
    abstract suspend fun insertItem(item: Item): Long

    @Query("SELECT * FROM batches WHERE itemId = :itemId AND batchNo = :batchNo LIMIT 1")
    abstract suspend fun findBatch(itemId: Long, batchNo: String): Batch?

    @Query("SELECT * FROM batches WHERE itemId = :itemId ORDER BY batchNo")
    abstract fun batchesOf(itemId: Long): Flow<List<Batch>>

    @Insert
    abstract suspend fun insertBatch(batch: Batch): Long

    @Update
    abstract suspend fun updateBatch(batch: Batch)

    @Insert
    abstract suspend fun insertTrx(trx: Trx)

    @Query("SELECT * FROM transactions ORDER BY time DESC LIMIT 500")
    abstract fun history(): Flow<List<Trx>>

    @Transaction
    open suspend fun stockIn(item: Item, batchNo: String, qty: Int, expiry: String, note: String) {
        val b = findBatch(item.id, batchNo)
        if (b == null) {
            insertBatch(Batch(itemId = item.id, batchNo = batchNo, qty = qty, expiry = expiry))
        } else {
            updateBatch(b.copy(qty = b.qty + qty, expiry = if (expiry.isNotBlank()) expiry else b.expiry))
        }
        insertTrx(Trx(itemId = item.id, itemCode = item.code, itemName = item.name,
            batchNo = batchNo, type = "MASUK", qty = qty, note = note, time = System.currentTimeMillis()))
    }

    @Transaction
    open suspend fun stockOut(item: Item, batchNo: String, qty: Int, note: String): Boolean {
        val b = findBatch(item.id, batchNo) ?: return false
        if (b.qty < qty) return false
        updateBatch(b.copy(qty = b.qty - qty))
        insertTrx(Trx(itemId = item.id, itemCode = item.code, itemName = item.name,
            batchNo = batchNo, type = "KELUAR", qty = qty, note = note, time = System.currentTimeMillis()))
        return true
    }
}

@Database(entities = [Item::class, Batch::class, Trx::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): StockDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "stok.db")
                .build().also { instance = it }
        }
    }
}
