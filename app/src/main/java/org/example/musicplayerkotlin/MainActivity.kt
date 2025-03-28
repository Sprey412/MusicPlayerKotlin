package org.example.musicplayerkotlin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var sbProgress: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnNext: Button
    private lateinit var btnRepeat: Button
    private lateinit var btnLoadLocal: Button
    private lateinit var lvTracks: ListView

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    private var handler = Handler()
    private var updateProgressRunnable: Runnable? = null

    // Room база данных
    private lateinit var db: AppDatabase

    private var tracksList = mutableListOf<Track>()
    private lateinit var tracksAdapter: ArrayAdapter<String>

    private var currentTrackIndex = 0
    // Режим повтора: 0 - Off, 1 - Repeat One, 2 - Repeat All
    private var repeatMode = 0

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Запрос разрешения на уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }

        // Инициализация UI-элементов
        ivCover = findViewById(R.id.iv_cover)
        tvTitle = findViewById(R.id.tv_title)
        tvArtist = findViewById(R.id.tv_artist)
        sbProgress = findViewById(R.id.sb_progress)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvDuration = findViewById(R.id.tv_duration)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnRepeat = findViewById(R.id.btn_repeat)
        btnLoadLocal = findViewById(R.id.btn_load_local)
        lvTracks = findViewById(R.id.lv_tracks)

        db = AppDatabase.getDatabase(this)

        tracksAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        lvTracks.adapter = tracksAdapter

        lvTracks.setOnItemClickListener { _, _, position, _ ->
            currentTrackIndex = position
            playTrack(tracksList[position])
        }

        sbProgress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pauseTrack()
            } else {
                if (mediaPlayer == null && tracksList.isNotEmpty()) {
                    playTrack(tracksList[currentTrackIndex])
                } else {
                    resumeTrack()
                }
            }
        }

        btnPrev.setOnClickListener {
            if (tracksList.isNotEmpty()) {
                currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else tracksList.size - 1
                playTrack(tracksList[currentTrackIndex])
            }
        }

        btnNext.setOnClickListener {
            if (tracksList.isNotEmpty()) {
                currentTrackIndex = if (currentTrackIndex < tracksList.size - 1) currentTrackIndex + 1 else 0
                playTrack(tracksList[currentTrackIndex])
            }
        }

        btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            val modeText = when (repeatMode) {
                0 -> "Repeat Off"
                1 -> "Repeat One"
                2 -> "Repeat All"
                else -> "Repeat Off"
            }
            Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
        }

        btnLoadLocal.setOnClickListener {
            checkPermissionsAndLoadTracks()
        }
    }

    private fun checkPermissionsAndLoadTracks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), PERMISSION_REQUEST_CODE)
            } else {
                loadLocalTracks()
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            } else {
                loadLocalTracks()
            }
        }
    }

    private fun loadLocalTracks() {
        Thread {
            db.trackDao().deleteAllTracks()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA
            )
            val cursor: Cursor? = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (it.moveToNext()) {
                    val title = it.getString(titleColumn) ?: "Unknown Title"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val album = it.getString(albumColumn) ?: "Unknown Album"
                    val data = it.getString(dataColumn) ?: ""
                    val track = Track(title = title, artist = artist, album = album, filePath = data)
                    db.trackDao().insertTrack(track)
                }
            }
            tracksList = db.trackDao().getAllTracks().toMutableList()
            runOnUiThread {
                tracksAdapter.clear()
                tracksAdapter.addAll(tracksList.map { "${it.title} - ${it.artist}" })
                tracksAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Loaded ${tracksList.size} local tracks", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun playTrack(track: Track) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            val file = File(track.filePath)
            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            isPlaying = true
            btnPlayPause.text = "⏸"

            tvTitle.text = track.title
            tvArtist.text = track.artist

            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(track.filePath)
                val artBytes = mmr.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    ivCover.setImageBitmap(bitmap)
                } else {
                    ivCover.setImageResource(R.drawable.placeholder_cover)
                }
            } catch (e: Exception) {
                ivCover.setImageResource(R.drawable.placeholder_cover)
            } finally {
                mmr.release()
            }

            sbProgress.max = mediaPlayer?.duration ?: 0

            updateProgressRunnable?.let { handler.removeCallbacks(it) }
            updateProgressRunnable = object : Runnable {
                override fun run() {
                    mediaPlayer?.let { mp ->
                        val currentPos = mp.currentPosition
                        sbProgress.progress = currentPos
                        tvCurrentTime.text = formatTime(currentPos)
                        tvDuration.text = formatTime(mp.duration)
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            handler.post(updateProgressRunnable!!)

            mediaPlayer?.setOnCompletionListener {
                isPlaying = false
                btnPlayPause.text = "▶"
                when (repeatMode) {
                    1 -> { // Повтор одного
                        playTrack(tracksList[currentTrackIndex])
                    }
                    2 -> { // Повтор всех
                        currentTrackIndex = if (currentTrackIndex < tracksList.size - 1) currentTrackIndex + 1 else 0
                        playTrack(tracksList[currentTrackIndex])
                    }
                    else -> {
                        // Без повтора
                    }
                }
                showNotification("Finished: ${track.title}")
            }

            showNotification("Playing: ${track.title}")
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing track", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun pauseTrack() {
        mediaPlayer?.pause()
        isPlaying = false
        btnPlayPause.text = "▶"
        showNotification("Paused")
    }

    private fun resumeTrack() {
        mediaPlayer?.start()
        isPlaying = true
        btnPlayPause.text = "⏸"
        showNotification("Resumed")
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun showNotification(contentText: String) {
        val channelId = "music_player_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Music Player")
            .setContentText(contentText)
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(this).notify(1, notification)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadLocalTracks()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
