package org.example.musicplayerkotlin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    // Элементы управления плеером
    private var btnPlay: Button? = null
    private var btnPause: Button? = null
    private var btnForward: Button? = null
    private var btnRewind: Button? = null
    private var btnNext: Button? = null
    private var btnPrev: Button? = null
    private var btnRepeat: Button? = null
    private var btnLoadLocal: Button? = null

    private var txtTimer: TextView? = null
    private var txtDuration: TextView? = null
    private var txtTitle: TextView? = null
    private var seekBarPlayback: SeekBar? = null
    private var seekBarVolume: SeekBar? = null

    // Для списка локальных треков
    private var listViewTracks: ListView? = null
    private var tracksListAdapter: ArrayAdapter<String>? = null
    private var localTracksTitles = mutableListOf<String>()

    private var mediaPlayer: MediaPlayer? = null
    private var handler = Handler()

    private var startTime = 0.0
    private var finalTime = 0.0
    private val forwardTime = 5000
    private val backwardTime = 5000

    private var oneTimeOnly = 0

    // Список треков из ресурсов
    private val trackList = arrayOf(R.raw.song)
    private var currentTrackIndex = 0

    // Режим повторения: 0 – Off, 1 – Repeat One, 2 – Repeat All, 3 – Shuffle
    private var repeatMode = 0

    // Room База данных
    private lateinit var db: AppDatabase

    // Runnable для обновления SeekBar
    private val updateSongTime = object : Runnable {
        override fun run() {
            if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
                startTime = mediaPlayer!!.currentPosition.toDouble()
                txtTimer!!.text = String.format(
                    "%d min, %d sec",
                    TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()),
                    TimeUnit.MILLISECONDS.toSeconds(startTime.toLong()) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()))
                )
                seekBarPlayback!!.progress = startTime.toInt()
            }
            handler.postDelayed(this, 100)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        // Инициализация элементов управления из XML
        btnPlay = findViewById(R.id.button)
        btnPause = findViewById(R.id.button2)
        btnForward = findViewById(R.id.button3)
        btnRewind = findViewById(R.id.button4)
        btnNext = findViewById(R.id.button_next)
        btnPrev = findViewById(R.id.button_prev)
        btnRepeat = findViewById(R.id.button_repeat)
        btnLoadLocal = findViewById(R.id.button_load_local)

        txtTimer = findViewById(R.id.text_view_2)
        txtDuration = findViewById(R.id.text_view_3)
        txtTitle = findViewById(R.id.text_view_4)
        seekBarPlayback = findViewById(R.id.seek_bar)
        seekBarVolume = findViewById(R.id.seekBar_volume)
        listViewTracks = findViewById(R.id.list_view_tracks)

        // Настройка адаптера для списка локальных треков
        tracksListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, localTracksTitles)
        listViewTracks!!.adapter = tracksListAdapter

        // Инициализация базы данных
        db = AppDatabase.getDatabase(this)

        // Установка начального заголовка
        txtTitle!!.text = "song.mp3"

        // Настройка SeekBar для перемотки
        seekBarPlayback!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer!!.seekTo(progress)
                    startTime = progress.toDouble()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Настройка регулировки громкости
        seekBarVolume!!.max = 100
        seekBarVolume!!.progress = 50
        seekBarVolume!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress / 100.0f
                mediaPlayer?.setVolume(volume, volume)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Первоначальное воспроизведение трека из ресурсов
        playTrack(currentTrackIndex)

        // Обработка кнопок управления плеером
        btnPlay!!.setOnClickListener {
            Toast.makeText(applicationContext, "Playing", Toast.LENGTH_SHORT).show()
            mediaPlayer?.start()
            finalTime = mediaPlayer?.duration?.toDouble() ?: 0.0
            startTime = mediaPlayer?.currentPosition?.toDouble() ?: 0.0
            if (oneTimeOnly == 0) {
                seekBarPlayback!!.max = finalTime.toInt()
                oneTimeOnly = 1
            }
            txtDuration!!.text = String.format(
                "%d min, %d sec",
                TimeUnit.MILLISECONDS.toMinutes(finalTime.toLong()),
                TimeUnit.MILLISECONDS.toSeconds(finalTime.toLong()) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(finalTime.toLong()))
            )
            handler.postDelayed(updateSongTime, 100)
            btnPause!!.isEnabled = true
            btnPlay!!.isEnabled = false
            showNotification("Playing: ${txtTitle!!.text}")
        }
        btnPause!!.setOnClickListener {
            Toast.makeText(applicationContext, "Paused", Toast.LENGTH_SHORT).show()
            mediaPlayer?.pause()
            btnPause!!.isEnabled = false
            btnPlay!!.isEnabled = true
            showNotification("Paused: ${txtTitle!!.text}")
        }
        btnForward!!.setOnClickListener {
            val temp = startTime.toInt()
            if (temp + forwardTime <= finalTime) {
                startTime += forwardTime
                mediaPlayer?.seekTo(startTime.toInt())
                Toast.makeText(applicationContext, "Forward 5 seconds", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Error", Toast.LENGTH_SHORT).show()
            }
        }
        btnRewind!!.setOnClickListener {
            val temp = startTime.toInt()
            if (temp - backwardTime >= 0) {
                startTime -= backwardTime
                mediaPlayer?.seekTo(startTime.toInt())
                Toast.makeText(applicationContext, "Rewind 5 seconds", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Error", Toast.LENGTH_SHORT).show()
            }
        }
        btnNext!!.setOnClickListener {
            currentTrackIndex = if (currentTrackIndex < trackList.size - 1) currentTrackIndex + 1 else 0
            playTrack(currentTrackIndex)
        }
        btnPrev!!.setOnClickListener {
            currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else trackList.size - 1
            playTrack(currentTrackIndex)
        }
        btnRepeat!!.setOnClickListener {
            repeatMode = (repeatMode + 1) % 4
            val modeText = when (repeatMode) {
                0 -> "Repeat: Off"
                1 -> "Repeat: One"
                2 -> "Repeat: All"
                3 -> "Shuffle"
                else -> "Repeat: Off"
            }
            btnRepeat!!.text = modeText
            Toast.makeText(applicationContext, modeText, Toast.LENGTH_SHORT).show()
        }

        // Обработка кнопки загрузки локальных треков
        btnLoadLocal!!.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                loadLocalTracks()
            }
        }
    }

    // Метод воспроизведения трека из ресурсов
    private fun playTrack(index: Int) {
        mediaPlayer?.reset()
        mediaPlayer?.release()
        oneTimeOnly = 0
        mediaPlayer = MediaPlayer.create(applicationContext, trackList[index])
        txtTitle!!.text = "Track ${index + 1}"
        val volume = (seekBarVolume?.progress ?: 50) / 100.0f
        mediaPlayer?.setVolume(volume, volume)
        mediaPlayer?.setOnCompletionListener {
            when (repeatMode) {
                1 -> { // Повтор одного
                    playTrack(currentTrackIndex)
                    mediaPlayer?.start()
                }
                2 -> { // Повтор плейлиста
                    currentTrackIndex = (currentTrackIndex + 1) % trackList.size
                    playTrack(currentTrackIndex)
                    mediaPlayer?.start()
                }
                3 -> { // Случайное воспроизведение
                    currentTrackIndex = Random().nextInt(trackList.size)
                    playTrack(currentTrackIndex)
                    mediaPlayer?.start()
                }
                else -> {
                    btnPlay!!.isEnabled = true
                    btnPause!!.isEnabled = false
                }
            }
            showNotification("Finished: ${txtTitle!!.text}")
        }
        finalTime = mediaPlayer?.duration?.toDouble() ?: 0.0
        seekBarPlayback?.max = finalTime.toInt()
    }

    // Метод для создания уведомления (stub)
    private fun showNotification(contentText: String) {
        val channelId = "music_player_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Music Player")
            .setContentText(contentText)
            .setOngoing(true)
            .build()
        with(NotificationManagerCompat.from(this)) {
            notify(1, notification)
        }
    }

    // Метод для загрузки локальных треков через MediaStore и сохранения в Room
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
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val data = cursor.getString(dataColumn) ?: ""
                    val track = Track(title = title, artist = artist, album = album, filePath = data)
                    db.trackDao().insertTrack(track)
                }
            }
            runOnUiThread {
                localTracksTitles.clear()
                val tracks = db.trackDao().getAllTracks()
                for (track in tracks) {
                    localTracksTitles.add("${track.title} - ${track.artist}")
                }
                tracksListAdapter?.notifyDataSetChanged()
                Toast.makeText(this, "Loaded ${localTracksTitles.size} local tracks", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // Обработка результата запроса разрешения
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadLocalTracks()
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
