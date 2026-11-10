package com.example.digitalbooks

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InicioSecion : AppCompatActivity() {
    private lateinit var etNombre: EditText
    private lateinit var etApellido: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPass: EditText          // confirmar contraseña
    private lateinit var btnRegistrar: Button
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_inicio_secion)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etNombre = findViewById(R.id.etNombre)
        etApellido = findViewById(R.id.etApellido)
        etCorreo = findViewById(R.id.etCorreo)
        etPassword = findViewById(R.id.etPassword)
        etPass = findViewById(R.id.etPass)
        btnRegistrar = findViewById(R.id.btnRegistrar)

        dbHelper = DatabaseHelper(this)

        // Ahora el botón SÍ llama a la función
        btnRegistrar.setOnClickListener {
            registrarUsuario()
        }
    }

    // La función va FUERA de onCreate, a nivel de clase
    private fun registrarUsuario() {
        val nombre = etNombre.text.toString().trim()
        val apellido = etApellido.text.toString().trim()
        val correo = etCorreo.text.toString().trim().lowercase()
        val password = etPassword.text.toString().trim()
        val confirmar = etPass.text.toString().trim()

        // 1. Campos vacíos
        if (nombre.isEmpty() || apellido.isEmpty() || correo.isEmpty() ||
            password.isEmpty() || confirmar.isEmpty()
        ) {
            Toast.makeText(this, "Por favor, complete todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Formato de correo
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.error = "Correo electrónico no válido"
            etCorreo.requestFocus()
            return
        }

        // 3. Longitud mínima de contraseña
        if (password.length < 6) {
            etPassword.error = "Mínimo 6 caracteres"
            etPassword.requestFocus()
            return
        }

        // 4. Las contraseñas deben coincidir
        if (password != confirmar) {
            etPass.error = "Las contraseñas no coinciden"
            etPass.requestFocus()
            return
        }

        // 5. Guardar (la tabla solo tiene "nombre", así que se une con el apellido)
        val resultado = dbHelper.registrarUsuario("$nombre $apellido", correo, password)
        if (resultado) {
            Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
            finish() // vuelve al login
        } else {
            // Sin finish(): que pueda corregir los datos
            Toast.makeText(this, "Ese correo ya está registrado", Toast.LENGTH_SHORT).show()
        }
    }
}