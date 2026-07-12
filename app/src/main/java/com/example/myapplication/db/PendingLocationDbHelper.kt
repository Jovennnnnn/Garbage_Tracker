package com.example.myapplication.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PendingLocationDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pending_locations.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_NAME = "pending_updates"
        const val COLUMN_ID = "id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_SPEED = "speed"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_STATUS = "status"
        const val COLUMN_IS_FULL = "is_full"
        const val COLUMN_TRUCK_ID = "truck_id"
        const val COLUMN_DRIVER_ID = "driver_id"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_LATITUDE + " REAL,"
                + COLUMN_LONGITUDE + " REAL,"
                + COLUMN_SPEED + " REAL,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_STATUS + " TEXT,"
                + COLUMN_IS_FULL + " INTEGER,"
                + COLUMN_TRUCK_ID + " TEXT,"
                + COLUMN_DRIVER_ID + " INTEGER" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
        onCreate(db)
    }

    fun insertPendingLocation(
        lat: Double, lng: Double, speed: Double, timestamp: Long, 
        status: String, isFull: Boolean, truckId: String, driverId: Int
    ) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LATITUDE, lat)
            put(COLUMN_LONGITUDE, lng)
            put(COLUMN_SPEED, speed)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_STATUS, status)
            put(COLUMN_IS_FULL, if (isFull) 1 else 0)
            put(COLUMN_TRUCK_ID, truckId)
            put(COLUMN_DRIVER_ID, driverId)
        }
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun getAllPendingLocations(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP ASC", null)
        
        if (cursor.moveToFirst()) {
            do {
                val map = mutableMapOf<String, Any>()
                map["id"] = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                map["lat"] = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE))
                map["lng"] = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                map["speed"] = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SPEED))
                map["timestamp"] = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                map["status"] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
                map["isFull"] = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_FULL)) == 1
                map["truckId"] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRUCK_ID))
                map["driverId"] = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DRIVER_ID))
                list.add(map)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun deleteLocation(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }
}
