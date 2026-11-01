package com.stokgudang.app

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Membuka pemindai barcode/QR dan mengirim isinya ke [onResult]. */
fun scanCode(ctx: Context, onResult: (String) -> Unit) {
    GmsBarcodeScanning.getClient(ctx).startScan()
        .addOnSuccessListener { b -> b.rawValue?.let(onResult) }
        .addOnFailureListener {
            Toast.makeText(ctx, "Scan gagal atau dibatalkan", Toast.LENGTH_SHORT).show()
        }
}

/** Membuat gambar QR dari teks (kode unik barang). */
fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}
