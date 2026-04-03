package com.n8n.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private var server: HttpServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        createNotificationChannel()
        startWebhookServer()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("webhook_notifications", "Webhook Notifications", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startWebhookServer() {
        Thread {
            try {
                server = HttpServer.create(InetSocketAddress(8000), 0)
                server?.createContext("/webhook") { exchange ->
                    if (exchange.requestMethod == "POST") {
                        val body = exchange.requestBody.bufferedReader().readText()
                        val json = JSONObject(body)
                        val title = json.optString("title", "n8n Alert")
                        val message = json.optString("message", "New notification received")

                        showNotification(title, message)

                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, 0)
                        val response = "{\"status\":\"ok\"}"
                        exchange.responseBody.write(response.toByteArray())
                        exchange.close()
                    } else {
                        exchange.sendResponseHeaders(405, 0)
                        exchange.close()
                    }
                }
                server?.executor = Executors.newCachedThreadPool()
                server?.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showNotification(title: String, message: String) {
        val notificationId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(this, "webhook_notifications")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(notificationId, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(0)
    }
}
