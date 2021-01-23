package com.udacity

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*


class MainActivity : AppCompatActivity() {

    private var downloadID: Long = 0
    private var downloadStatus = "Fail"
    private lateinit var selectedDownloadUri: URL

    private val NOTIFICATION_ID = 0

    private lateinit var notificationManager: NotificationManager
    private lateinit var pendingIntent: PendingIntent
    private lateinit var action: NotificationCompat.Action

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        createNotificationChannel()

        registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        download_radio_group.setOnCheckedChangeListener { radioGroup, i ->
            selectedDownloadUri = when(i){
                R.id.retrofit_radio_button -> URL.RETROFIT_URI
                R.id.udacity_radio_button -> URL.UDACITY_URI
                R.id.glide_radio_button -> URL.GLIDE_URI
                else -> URL.RETROFIT_URI
            }
        }

        custom_button.setOnClickListener {
            if (this::selectedDownloadUri.isInitialized){
                val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
                val isConnected: Boolean = activeNetwork?.isConnectedOrConnecting == true
                if(isConnected){
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        custom_button.buttonState = ButtonState.Loading
                        download()
                    } else {
                        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                PermissionInfo.PROTECTION_DANGEROUS);
                    }
                } else {
                    Toast.makeText(this, getString(R.string.no_network_toast), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.select_option_toast), Toast.LENGTH_SHORT).show()
            }

        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if(downloadID == id){
                downloadStatus = "Success"
                custom_button.buttonState = ButtonState.Completed
                createNotification()
            }
        }
    }

    private fun download() {
        val request =
            DownloadManager.Request(Uri.parse(selectedDownloadUri.uri))
                    .setTitle(getString(R.string.app_name))
                    .setDescription(getString(R.string.app_description))
                    .setRequiresCharging(false)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"/repository.zip" )
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadID =
            downloadManager.enqueue(request)// enqueue puts the download request in the queue.

        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if(cursor.moveToFirst()){
            when (cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)){
                DownloadManager.STATUS_FAILED -> {
                    downloadStatus = "Fail"
                    custom_button.buttonState = ButtonState.Completed }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadStatus = "Success"
                }
            }
        }
    }

    private fun createNotification(){
        notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) as NotificationManager

        val detailIntent = Intent(this, DetailActivity::class.java)
        detailIntent.putExtra("fileName", selectedDownloadUri.title)
        detailIntent.putExtra("status", downloadStatus)
        pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(detailIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        } as PendingIntent
        action = NotificationCompat.Action(R.drawable.ic_assistant_black_24dp, getString(R.string.notification_button), pendingIntent)

        val contentIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_assistant_black_24dp)
            .setContentTitle(selectedDownloadUri.title)
            .setContentText(selectedDownloadUri.text)
            .setContentIntent(contentPendingIntent)
            .addAction(action)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "LoadAppChannel",
                NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(false) }
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.enableVibration(true)
            notificationChannel.description = "Download complete!"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    companion object {
        private enum class URL (val uri: String, val title: String, val text: String) {
            GLIDE_URI(
                 "https://github.com/bumptech/glide/archive/master.zip",
                "Glide: Image Loading Library By BumpTech",
                "Glide repository is downloaded"
            ),
            UDACITY_URI(
                 "https://github.com/udacity/nd940-c3-advanced-android-programming-project-starter/archive/master.zip",
                "Udacity: Android Kotlin Nanodegree",
                "The Project 3 repository is downloaded"),
            RETROFIT_URI(
                 "https://github.com/square/retrofit/archive/master.zip",
                "Retrofit: Type-safe HTTP client by Square, Inc",
                "Retrofit repository is downloaded"),
        }
        private const val CHANNEL_ID = "channelId"
    }

}
