package com.stokgudang.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StockViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    val rows = dao.itemsWithTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun batchesOf(itemId: Long) = dao.batchesOf(itemId)

    suspend fun find(code: String): Item? = dao.findByCode(code.trim().uppercase())

    fun addItem(code: String, name: String, unit: String, done: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val c = code.trim().uppercase()
            when {
                c.isEmpty() || name.isBlank() -> done(false, "Kode dan nama wajib diisi")
                dao.findByCode(c) != null -> done(false, "Kode $c sudah dipakai")
                else -> {
                    dao.insertItem(Item(code = c, name = name.trim(), unit = unit.trim().ifEmpty { "pcs" }))
                    done(true, "Barang $c ditambahkan")
                }
            }
        }
    }

    fun stockIn(item: Item, batchNo: String, qty: Int, expiry: String, note: String,
                done: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            dao.stockIn(item, batchNo.trim().uppercase(), qty, expiry.trim(), note.trim())
            done(true, "Masuk $qty ${item.unit} (batch ${batchNo.trim().uppercase()})")
        }
    }

    fun stockOut(item: Item, batchNo: String, qty: Int, note: String,
                 done: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val ok = dao.stockOut(item, batchNo, qty, note.trim())
            done(ok, if (ok) "Keluar $qty ${item.unit} (batch $batchNo)" else "Stok batch tidak cukup")
        }
    }
}
