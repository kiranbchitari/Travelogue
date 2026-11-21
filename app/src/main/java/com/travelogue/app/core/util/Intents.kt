package com.travelogue.app.core.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Thin wrappers around Android intents. Every call fails soft with a toast instead of crashing. */
object Intents {
    private fun Context.launch(intent: Intent, failMessage: String) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun dial(context: Context, phone: String) =
        context.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")), "No phone app found")

    fun sms(context: Context, phone: String) =
        context.launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")), "No messaging app found")

    fun whatsapp(context: Context, phone: String) {
        val digits = phone.filter { it.isDigit() }
        context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")), "WhatsApp is not installed")
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun openUrl(context: Context, url: String) {
        val fixed = if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("geo:")) url else "https://$url"
        context.launch(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)), "No app can open this link")
    }

    /** Opens coordinates in whatever maps app is installed, no Maps API needed. */
    fun openCoordinates(context: Context, lat: Double, lng: Double, label: String) =
        context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")), "No maps app found")

    fun searchAddress(context: Context, address: String) =
        context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")), "No maps app found")

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Opens an app-private file (PDF, image) in an external viewer with a temporary read grant. */
    fun openFile(context: Context, file: File, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(context, file), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.launch(intent, "No app can open this file")
    }
}
