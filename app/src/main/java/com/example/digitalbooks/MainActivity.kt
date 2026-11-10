package com.example.digitalbooks

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    // Solo los campos que EXISTEN en activity_main.xml
    private lateinit var etCorreoLogin: EditText
    private lateinit var etPasswordLogin: EditText
    private lateinit var btnIniciar: Button
    private lateinit var btnCrearCuenta: TextView
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Los IDs deben coincidir con los del XML
        etCorreoLogin = findViewById(R.id.etCorreoLogin)
        etPasswordLogin = findViewById(R.id.etPasswordLogin)
        btnIniciar = findViewById(R.id.btnIniciar)
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta)

        dbHelper = DatabaseHelper(this)

        // Botón de iniciar sesión
        btnIniciar.setOnClickListener {
            iniciarSesion()
        }

        // Texto "Crear una Cuenta" -> pantalla de registro
        btnCrearCuenta.setOnClickListener {
            startActivity(Intent(this, InicioSecion::class.java))
        }
    }

    // La función va FUERA de onCreate, a nivel de clase
    private fun iniciarSesion() {
        val correo = etCorreoLogin.text.toString().trim().lowercase()
        val password = etPasswordLogin.text.toString().trim()

        // 1. Campos vacíos
        if (correo.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Formato de correo
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreoLogin.error = "Correo electrónico no válido"
            etCorreoLogin.requestFocus()
            return
        }

        // 3. Verificar en la base de datos
        if (dbHelper.iniciarSesion(correo, password)) {
            Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        } else {
            // Sin finish(): el usuario debe poder reintentar
            Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
        }
    }
}