package com.example.digitalbooks

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "DigitalBooks.db"
        private const val DATABASE_VERSION = 2   // subido de 1 a 2 por el cambio de columna
        private const val TABLE_USUARIOS = "usuarios"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NOMBRE = "nombre"
        private const val COLUMN_CORREO = "correo"
        private const val COLUMN_PASSWORD = "password"   // antes "contraseña" (la ñ da problemas)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTableQuery = """
            CREATE TABLE $TABLE_USUARIOS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NOMBRE TEXT NOT NULL,
                $COLUMN_CORREO TEXT NOT NULL UNIQUE,
                $COLUMN_PASSWORD TEXT NOT NULL
            )
        """.trimIndent()
        db?.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_USUARIOS")
        onCreate(db)
    }

    fun registrarUsuario(nombre: String, correo: String, password: String): Boolean {
        val db = writableDatabase
        val datos = ContentValues().apply {
            put(COLUMN_NOMBRE, nombre)
            put(COLUMN_CORREO, correo)
            put(COLUMN_PASSWORD, password)
        }
        // insert devuelve -1 si falla (por ejemplo, correo repetido)
        return db.insert(TABLE_USUARIOS, null, datos) != -1L
    }

    fun iniciarSesion(correo: String, password: String): Boolean {
        val db = readableDatabase
        val sql = "SELECT $COLUMN_ID FROM $TABLE_USUARIOS WHERE $COLUMN_CORREO = ? AND $COLUMN_PASSWORD = ?"
        // use{} cierra el cursor solo. NO se cierra db: el helper la administra.
        return db.rawQuery(sql, arrayOf(correo, password)).use { it.count > 0 }
    }
}