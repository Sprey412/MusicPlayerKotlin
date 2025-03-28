package org.example.musicplayerkotlin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun getAllTracks(): List<Track>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: Track)

    @Query("DELETE FROM tracks")
    fun deleteAllTracks()
}
