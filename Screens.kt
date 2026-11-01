package com.stokgudang.app

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun App(vm: StockViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Stok" to "📦", "Masuk" to "⬇️", "Keluar" to "⬆️", "Riwayat" to "🕘")
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, emoji) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(emoji) }, label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> StokScreen(vm)
                1 -> MasukScreen(vm)
                2 -> KeluarScreen(vm)
                else -> RiwayatScreen(vm)
            }
        }
    }
}

@Composable
fun StokScreen(vm: StockViewModel) {
    val ctx = LocalContext.current
    val rows by vm.rows.collectAsState()
    var q by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ItemRow?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Stok Barang", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("Cari kode / nama") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Tambah Barang Baru")
        }
        val list = rows.filter { it.code.contains(q, true) || it.name.contains(q, true) }
        if (list.isEmpty()) {
            Text("Belum ada barang.", Modifier.padding(top = 16.dp))
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(list, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { detail = r }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Bold)
                            Text("Kode: ${r.code}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${r.total} ${r.unit}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showAdd) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("pcs") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Barang Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code, { code = it }, singleLine = true, label = { Text("Kode unik") })
                    Row {
                        TextButton(onClick = { code = "BRG-" + (100000..999999).random() }) {
                            Text("Kode otomatis")
                        }
                        TextButton(onClick = { scanCode(ctx) { code = it } }) { Text("📷 Scan") }
                    }
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nama barang") })
                    OutlinedTextField(unit, { unit = it }, singleLine = true, label = { Text("Satuan (pcs, kg, dus)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addItem(code, name, unit) { ok, msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        if (ok) showAdd = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Batal") } }
        )
    }

    detail?.let { r ->
        val flow = remember(r.id) { vm.batchesOf(r.id) }
        val batches by flow.collectAsState(initial = emptyList<Batch>())
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("${r.name} (${r.code})") },
            text = {
                Column {
                    Text("Total: ${r.total} ${r.unit}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val qr = remember(r.code) { qrBitmap(r.code).asImageBitmap() }
                    Image(
                        bitmap = qr, contentDescription = "QR ${r.code}",
                        modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally)
                    )
                    Text("QR kode: ${r.code}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                    if (batches.isEmpty()) Text("Belum ada batch.")
                    batches.forEach { b ->
                        Text("• Batch ${b.batchNo}: ${b.qty} ${r.unit}" +
                            if (b.expiry.isNotBlank()) " (exp ${b.expiry})" else "")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Tutup") } }
        )
    }
}

@Composable
fun MasukScreen(vm: StockViewModel) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Item?>(null) }
    var batch by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(code) { found = if (code.isBlank()) null else vm.find(code) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Barang Masuk", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it }, singleLine = true,
                label = { Text("Kode unik barang") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { scanCode(ctx) { code = it } }) { Text("📷 Scan") }
        }
        Text(
            found?.let { "✔ ${it.name} (${it.unit})" }
                ?: if (code.isBlank()) "" else "Kode tidak ditemukan",
            color = if (found != null) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
        OutlinedTextField(batch, { batch = it }, singleLine = true,
            label = { Text("Nomor batch") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, singleLine = true,
            label = { Text("Jumlah") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(exp, { exp = it }, singleLine = true,
            label = { Text("Kedaluwarsa (opsional, 2027-12-31)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Catatan (supplier, no. surat jalan)") },
            modifier = Modifier.fillMaxWidth())
        Button(
            enabled = found != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val n = qty.toIntOrNull() ?: 0
                if (batch.isBlank() || n <= 0) {
                    Toast.makeText(ctx, "Isi nomor batch dan jumlah (> 0)", Toast.LENGTH_SHORT).show()
                } else {
                    vm.stockIn(found!!, batch, n, exp, note) { _, msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        batch = ""; qty = ""; exp = ""; note = ""
                    }
                }
            }
        ) { Text("Simpan Barang Masuk") }
    }
}

@Composable
fun KeluarScreen(vm: StockViewModel) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Item?>(null) }
    var sel by remember { mutableStateOf<Batch?>(null) }
    var qty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(code) { found = if (code.isBlank()) null else vm.find(code); sel = null }
    val flow = remember(found) { found?.let { vm.batchesOf(it.id) } ?: flowOf(emptyList<Batch>()) }
    val batches by flow.collectAsState(initial = emptyList<Batch>())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Barang Keluar", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it }, singleLine = true,
                label = { Text("Kode unik barang") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { scanCode(ctx) { code = it } }) { Text("📷 Scan") }
        }
        Text(
            found?.let { "✔ ${it.name} (${it.unit})" }
                ?: if (code.isBlank()) "" else "Kode tidak ditemukan",
            color = if (found != null) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
        if (found != null) {
            Text("Pilih batch:", fontWeight = FontWeight.Bold)
            val avail = batches.filter { it.qty > 0 }
            if (avail.isEmpty()) Text("Tidak ada stok tersedia.")
            avail.forEach { b ->
                Row(Modifier.fillMaxWidth().clickable { sel = b }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sel?.id == b.id, onClick = { sel = b })
                    Text("Batch ${b.batchNo} — sisa ${b.qty}" +
                        if (b.expiry.isNotBlank()) " (exp ${b.expiry})" else "")
                }
            }
        }
        OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, singleLine = true,
            label = { Text("Jumlah keluar") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(note, { note = it }, label = { Text("Catatan (tujuan, penerima)") },
            modifier = Modifier.fillMaxWidth())
        Button(
            enabled = found != null && sel != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val n = qty.toIntOrNull() ?: 0
                if (n <= 0) {
                    Toast.makeText(ctx, "Jumlah harus lebih dari 0", Toast.LENGTH_SHORT).show()
                } else {
                    vm.stockOut(found!!, sel!!.batchNo, n, note) { ok, msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        if (ok) { qty = ""; note = ""; sel = null }
                    }
                }
            }
        ) { Text("Simpan Barang Keluar") }
    }
}

@Composable
fun RiwayatScreen(vm: StockViewModel) {
    val hist by vm.history.collectAsState()
    val fmt = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Riwayat Transaksi", style = MaterialTheme.typography.headlineSmall)
        if (hist.isEmpty()) Text("Belum ada transaksi.", Modifier.padding(top = 16.dp))
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(hist, key = { it.id }) { t ->
                val masuk = t.type == "MASUK"
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(
                                (if (masuk) "⬇ MASUK " else "⬆ KELUAR ") + t.qty,
                                color = if (masuk) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                            )
                            Text(fmt.format(Date(t.time)), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${t.itemName} (${t.itemCode})")
                        Text("Batch: ${t.batchNo}", style = MaterialTheme.typography.bodySmall)
                        if (t.note.isNotBlank()) Text("Catatan: ${t.note}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
