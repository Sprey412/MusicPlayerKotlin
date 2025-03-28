package org.example.musicplayerkotlin

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import org.example.musicplayerkotlin.ui.theme.MusicPlayerKotlinTheme
import java.util.concurrent.TimeUnit
import java.util.logging.Handler

class MainActivity : ComponentActivity() {
    private var b1: Button?=null
    private var b2: Button?=null
    private var b3: Button?=null
    private var b4: Button?=null
    private val iv: ImageView?=null
    private var mediaPlayer: MediaPlayer?=null

    private var startTime = 0.0
    private var finalTime = 0.0
    private val forwardTime = 5000
    private val backwardTime = 5000

    private var seekbar: SeekBar?=null
    private var txt1:TextView?=null
    private var txt2:TextView?=null
    private var txt3:TextView?=null
    private val myHandler
        get() = android.os.Handler()

    private val updateSongTime = object : Runnable{
        override fun run() {
            startTime = mediaPlayer!!.currentPosition.toDouble()
            txt1!!.setText(String.format("%d min, %d sec", TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()),
                TimeUnit.MILLISECONDS.toSeconds(startTime.toLong()) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(startTime.toLong())
            )))
            seekbar!!.progress = startTime.toInt()

            myHandler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        b1 = findViewById<View>(R.id.button) as Button
        b2 = findViewById<View>(R.id.button2) as Button
        b3 = findViewById<View>(R.id.button3) as Button
        b4 = findViewById<View>(R.id.button4) as Button

        txt1 = findViewById<View>(R.id.text_view_2) as TextView
        txt2 = findViewById<View>(R.id.text_view_3) as TextView
        txt3 = findViewById<View>(R.id.text_view_4) as TextView

        txt3!!.text = "song.mp3"

        mediaPlayer = MediaPlayer.create(applicationContext,R.raw.song)
        seekbar = findViewById<View>(R.id.seek_bar) as SeekBar
        seekbar!!.isClickable = false
        b2!!.isEnabled = false

        b1!!.setOnClickListener {
            Toast.makeText(applicationContext,"Playing",Toast.LENGTH_SHORT).show()
            mediaPlayer!!.start()

            finalTime = mediaPlayer!!.duration.toDouble()
            startTime = mediaPlayer!!.currentPosition.toDouble()

            if(oneTimeOnly==0) {
                seekbar!!.max = finalTime.toInt()
                oneTimeOnly = 1
            }
            txt2!!.setText(String.format("%d min, %d sec", TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()),
                TimeUnit.MILLISECONDS.toSeconds(startTime.toLong()) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()))))

            txt1!!.setText(String.format("%d min, %d sec", TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()),
                TimeUnit.MILLISECONDS.toSeconds(startTime.toLong()) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(startTime.toLong()))))
            seekbar!!.progress = startTime.toInt()
            myHandler.postDelayed(updateSongTime,100)
            b2!!.isEnabled = true
            b1!!.isEnabled = false
        }
        b2!!.setOnClickListener {
            Toast.makeText(applicationContext,"Paused",Toast.LENGTH_SHORT).show()
            mediaPlayer!!.pause()
            b2!!.isEnabled = false
            b1!!.isEnabled = true
        }

        b3!!.setOnClickListener {
            val temp = startTime.toInt()
            if(temp + forwardTime <= finalTime) {
                startTime = startTime + forwardTime
                mediaPlayer!!.seekTo(startTime.toInt())
                Toast.makeText(applicationContext,"Forward 5 seconds",Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext,"Error",Toast.LENGTH_SHORT).show()

            }
        }
        b4!!.setOnClickListener {
            val temp = startTime.toInt()
            if(temp + forwardTime <= finalTime) {
                startTime = startTime - backwardTime
                mediaPlayer!!.seekTo(startTime.toInt())
                Toast.makeText(applicationContext,"Backward 5 seconds",Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext,"Error",Toast.LENGTH_SHORT).show()

            }
        }
    }
    companion object{
        var oneTimeOnly = 0
    }
}

