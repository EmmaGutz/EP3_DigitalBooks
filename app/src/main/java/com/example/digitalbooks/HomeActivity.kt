package com.example.digitalbooks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity() {

    private data class Categoria(val nombre: String, val consulta: String, val chipId: Int)

    private val categorias = listOf(
        Categoria("Ficción", "subject:fiction", R.id.chipFiccion),
        Categoria("Ciencia", "subject:science", R.id.chipCiencia),
        Categoria("Historia", "subject:history", R.id.chipHistoria)
    )

    // Separamos las consultas de las imágenes para que las portadas no bloqueen la búsqueda.
    private val ejecutorConsultas = Executors.newSingleThreadExecutor()
    private val ejecutorImagenes = Executors.newFixedThreadPool(3)
    private val cacheImagenes = LruCache<String, Bitmap>(30)
    @Volatile private var peticionActual = 0
    private var ultimaConsulta = "subject:fiction"

    private lateinit var buscador: EditText
    private lateinit var tituloSeccion: TextView
    private lateinit var mensajeEstado: TextView
    private lateinit var barraCarga: ProgressBar
    private lateinit var cuadricula: GridLayout
    private lateinit var espacios: List<FrameLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        buscador = findViewById(R.id.buscadorLibros)
        tituloSeccion = findViewById(R.id.tvTituloSeccion)
        mensajeEstado = findViewById(R.id.tvEstadoLibros)
        barraCarga = findViewById(R.id.barraCargaLibros)
        cuadricula = findViewById(R.id.cuadriculaLibros)

        espacios = listOf(
            R.id.libro_1, R.id.libro_2, R.id.libro_3,
            R.id.libro_4, R.id.libro_5, R.id.libro_6,
            R.id.libro_7, R.id.libro_8, R.id.libro_9
        ).map { findViewById<FrameLayout>(it) }
        espacios.forEach { it.clipToOutline = true }

        categorias.forEach { categoria ->
            findViewById<TextView>(categoria.chipId).setOnClickListener {
                buscador.text.clear()
                seleccionarCategoria(categoria)
            }
        }

        // La tecla de buscar del teclado funciona también con un teclado físico.
        buscador.setOnEditorActionListener { _, accion, evento ->
            val buscar = accion == EditorInfo.IME_ACTION_SEARCH ||
                (evento?.keyCode == KeyEvent.KEYCODE_ENTER && evento.action == KeyEvent.ACTION_DOWN)
            if (buscar) buscarPorTexto()
            buscar
        }

        // También permite buscar tocando la lupa que aparece dentro del campo.
        buscador.setOnTouchListener { vista, evento ->
            if (evento.action == MotionEvent.ACTION_UP &&
                buscador.compoundDrawablesRelative[0] != null &&
                evento.x <= buscador.totalPaddingLeft.toFloat()
            ) {
                buscarPorTexto()
                vista.performClick()
                true
            } else false
        }

        val secciones = listOf(
            R.id.navCategorias to "Categorías",
            R.id.navGuardados to "Guardados",
            R.id.navPerfil to "Perfil"
        )
        secciones.forEach { (id, nombre) ->
            findViewById<TextView>(id).setOnClickListener {
                Toast.makeText(this, "$nombre: próximamente", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.navInicio).setOnClickListener {
            buscador.text.clear()
            seleccionarCategoria(categorias.first())
        }

        seleccionarCategoria(categorias.first())
    }

    private fun buscarPorTexto() {
        val texto = buscador.text.toString().trim()
        if (texto.isEmpty()) {
            buscador.error = "Escribe un título o autor"
            return
        }
        marcarChip(null)
        tituloSeccion.text = "Resultados para \"$texto\""
        cargarLibros(texto)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(buscador.windowToken, 0)
        buscador.clearFocus()
    }

    private fun seleccionarCategoria(categoria: Categoria) {
        marcarChip(categoria)
        tituloSeccion.text = "Libros gratuitos: ${categoria.nombre}"
        cargarLibros(categoria.consulta)
    }

    private fun marcarChip(seleccionada: Categoria?) {
        categorias.forEach { categoria ->
            val chip = findViewById<TextView>(categoria.chipId)
            val activo = categoria == seleccionada
            chip.setBackgroundResource(if (activo) R.drawable.chip_on else R.drawable.chip_off)
            chip.setTextColor(if (activo) 0xFFFFFFFF.toInt() else 0xFF202020.toInt())
        }
    }

    private fun cargarLibros(consulta: String) {
        ultimaConsulta = consulta
        val id = ++peticionActual
        mostrarCargando()

        ejecutorConsultas.execute {
            // Al cambiar rápidamente de categoría, omite consultas que ya no interesan.
            if (id != peticionActual) return@execute
            try {
                val libros = GoogleBooksApi.buscar(consulta, espacios.size)
                runOnUiThread {
                    if (pantallaActual(id)) {
                        if (libros.isEmpty()) {
                            mostrarMensaje("No se encontraron libros gratuitos para esta búsqueda.", false)
                        } else {
                            guardarEnCache(consulta, libros)
                            mostrarLibros(libros, id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DigitalBooks", "No se pudo consultar el catálogo", e)
                val guardados = leerCache(consulta)
                runOnUiThread {
                    if (pantallaActual(id)) {
                        if (guardados.isNotEmpty()) {
                            mostrarLibros(guardados, id)
                            mensajeEstado.text = "Sin conexión. Se muestran los últimos libros guardados."
                            mensajeEstado.visibility = View.VISIBLE
                        } else {
                            mostrarMensaje(
                                "No se pudieron cargar los libros. Comprueba tu internet y toca aquí para reintentar.",
                                true
                            )
                        }
                    }
                }
            }
        }
    }

    private fun pantallaActual(id: Int): Boolean =
        id == peticionActual && !isFinishing && !isDestroyed

    private fun mostrarCargando() {
        barraCarga.visibility = View.VISIBLE
        cuadricula.visibility = View.GONE
        mensajeEstado.text = "Buscando libros gratuitos..."
        mensajeEstado.visibility = View.VISIBLE
        mensajeEstado.setOnClickListener(null)
    }

    private fun mostrarMensaje(texto: String, reintentar: Boolean) {
        barraCarga.visibility = View.GONE
        cuadricula.visibility = View.GONE
        mensajeEstado.text = texto
        mensajeEstado.visibility = View.VISIBLE
        if (reintentar) {
            mensajeEstado.setOnClickListener { cargarLibros(ultimaConsulta) }
        } else {
            mensajeEstado.setOnClickListener(null)
        }
    }

    private fun mostrarLibros(libros: List<GoogleBooksApi.Libro>, id: Int) {
        barraCarga.visibility = View.GONE
        mensajeEstado.visibility = View.GONE
        mensajeEstado.setOnClickListener(null)
        cuadricula.visibility = View.VISIBLE

        espacios.forEachIndexed { indice, espacio ->
            val libro = libros.getOrNull(indice)
            espacio.removeAllViews()
            espacio.setOnClickListener(null)

            if (libro == null) {
                espacio.visibility = View.GONE
                espacio.tag = null
                return@forEachIndexed
            }

            espacio.visibility = View.VISIBLE
            val identificador = "$id-$indice"
            espacio.tag = identificador
            espacio.contentDescription = "${libro.titulo}. ${libro.autores}"
            espacio.addView(crearTextoTitulo(libro.titulo))
            espacio.setOnClickListener { abrirLibro(libro) }

            libro.portadaUrl?.let { portada ->
                cargarImagen(portada) { imagen ->
                    // Evita colocar portadas de una búsqueda anterior.
                    if (pantallaActual(id) && espacio.tag == identificador) {
                        espacio.removeAllViews()
                        espacio.addView(ImageView(this).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(imagen)
                        })
                    }
                }
            }
        }
    }

    private fun abrirLibro(libro: GoogleBooksApi.Libro) {
        val enlace = libro.enlace
        if (enlace.isNullOrBlank()) {
            Toast.makeText(this, "${libro.titulo} - ${libro.autores}", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(enlace)))
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró una aplicación para abrir el libro", Toast.LENGTH_LONG).show()
        }
    }

    private fun crearTextoTitulo(titulo: String) = TextView(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        text = titulo
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 5
        val margen = (6 * resources.displayMetrics.density).toInt()
        setPadding(margen, margen, margen, margen)
    }

    private fun cargarImagen(url: String, alTerminar: (Bitmap) -> Unit) {
        if (!url.startsWith("https://")) return
        cacheImagenes.get(url)?.let { imagen ->
            alTerminar(imagen)
            return
        }

        ejecutorImagenes.execute {
            var conexion: HttpURLConnection? = null
            try {
                conexion = URL(url).openConnection() as HttpURLConnection
                conexion.connectTimeout = 7_000
                conexion.readTimeout = 7_000
                if (conexion.responseCode !in 200..299) return@execute
                val imagen = conexion.inputStream.use { BitmapFactory.decodeStream(it) }
                if (imagen != null) {
                    cacheImagenes.put(url, imagen)
                    runOnUiThread { if (!isFinishing && !isDestroyed) alTerminar(imagen) }
                }
            } catch (e: Exception) {
                Log.w("DigitalBooks", "Portada no disponible: $url", e)
            } finally {
                conexion?.disconnect()
            }
        }
    }

    // Guarda búsquedas exitosas para mostrar libros si luego se pierde internet.
    private fun guardarEnCache(consulta: String, libros: List<GoogleBooksApi.Libro>) {
        val datos = JSONArray()
        libros.forEach { libro ->
            datos.put(JSONObject().apply {
                put("titulo", libro.titulo)
                put("autores", libro.autores)
                put("portada", libro.portadaUrl)
                put("enlace", libro.enlace)
            })
        }
        getSharedPreferences("libros_cache", Context.MODE_PRIVATE)
            .edit().putString(consulta, datos.toString()).apply()
    }

    private fun leerCache(consulta: String): List<GoogleBooksApi.Libro> {
        val texto = getSharedPreferences("libros_cache", Context.MODE_PRIVATE)
            .getString(consulta, null) ?: return emptyList()
        return try {
            val datos = JSONArray(texto)
            val libros = ArrayList<GoogleBooksApi.Libro>()
            for (i in 0 until datos.length()) {
                val obj = datos.getJSONObject(i)
                libros.add(GoogleBooksApi.Libro(
                    obj.optString("titulo", "Sin título"),
                    obj.optString("autores", "Autor desconocido"),
                    obj.optString("portada").takeUnless { it == "null" || it.isBlank() },
                    obj.optString("enlace").takeUnless { it == "null" || it.isBlank() }
                ))
            }
            libros
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        peticionActual++
        ejecutorConsultas.shutdownNow()
        ejecutorImagenes.shutdownNow()
        super.onDestroy()
    }
}
