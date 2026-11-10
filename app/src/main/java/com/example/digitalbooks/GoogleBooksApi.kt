package com.example.digitalbooks

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca libros de lectura gratuita en Open Library y, si no hay resultados,
 * intenta Google Books. No necesita colocar claves privadas en el proyecto.
 * Todas estas funciones se ejecutan desde un hilo secundario.
 */
object GoogleBooksApi {

    data class Libro(
        val titulo: String,
        val autores: String,
        val portadaUrl: String?,
        val enlace: String?
    )

    fun buscar(consulta: String, maximo: Int = 9): List<Libro> {
        var huboRespuesta = false
        var ultimoError: Exception? = null

        // Open Library ofrece búsqueda sin clave y permite filtrar lectura pública.
        try {
            val libros = buscarOpenLibrary(consulta, maximo)
            huboRespuesta = true
            if (libros.isNotEmpty()) return libros
        } catch (e: Exception) {
            ultimoError = e
        }

        // Si la primera fuente no da libros, probamos Google Books.
        try {
            val libros = buscarGoogleBooks(consulta, maximo)
            huboRespuesta = true
            if (libros.isNotEmpty()) return libros
        } catch (e: Exception) {
            ultimoError = e
        }

        // No confundir «sin resultados» con «no se pudo conectar».
        if (huboRespuesta) return emptyList()
        throw IOException("No fue posible consultar las bibliotecas digitales", ultimoError)
    }

    private fun buscarOpenLibrary(consulta: String, maximo: Int): List<Libro> {
        // Solo libros con acceso público a su texto (sin préstamo o pago).
        val termino = "$consulta AND ebook_access:public"
        val campos = "key,title,author_name,cover_i,ebook_access"
        val url = "https://openlibrary.org/search.json" +
            "?q=${codificar(termino)}&fields=${codificar(campos)}" +
            "&limit=$maximo&lang=es"

        val datos = JSONObject(descargar(url))
        val docs = datos.optJSONArray("docs") ?: return emptyList()
        val resultado = ArrayList<Libro>()

        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val titulo = doc.optString("title").trim()
            if (titulo.isEmpty()) continue

            val autoresJson = doc.optJSONArray("author_name")
            val autores = if (autoresJson != null && autoresJson.length() > 0) {
                val nombres = ArrayList<String>()
                for (j in 0 until minOf(autoresJson.length(), 3)) {
                    nombres.add(autoresJson.optString(j))
                }
                nombres.joinToString(", ")
            } else "Autor desconocido"

            val portadaId = doc.optLong("cover_i", 0L)
            val portada = if (portadaId > 0L) {
                "https://covers.openlibrary.org/b/id/$portadaId-M.jpg?default=false"
            } else null

            val ruta = doc.optString("key")
            val enlace = if (ruta.startsWith("/works/") || ruta.startsWith("/books/")) {
                "https://openlibrary.org$ruta"
            } else null

            resultado.add(Libro(titulo, autores, portada, enlace))
        }
        return resultado
    }

    private fun buscarGoogleBooks(consulta: String, maximo: Int): List<Libro> {
        // La búsqueda de volúmenes públicos puede consultarse sin credenciales de usuario.
        // Si Google exige una clave en esta red, el error se maneja sin cerrar la app.
        val url = "https://www.googleapis.com/books/v1/volumes" +
            "?q=${codificar(consulta)}&filter=free-ebooks&printType=books" +
            "&maxResults=$maximo"

        val datos = JSONObject(descargar(url))
        val items = datos.optJSONArray("items") ?: return emptyList()
        val resultado = ArrayList<Libro>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val info = item.optJSONObject("volumeInfo") ?: continue
            val titulo = info.optString("title").trim()
            if (titulo.isEmpty()) continue

            val autoresJson = info.optJSONArray("authors")
            val autores = if (autoresJson != null && autoresJson.length() > 0) {
                val nombres = ArrayList<String>()
                for (j in 0 until autoresJson.length()) {
                    nombres.add(autoresJson.optString(j))
                }
                nombres.joinToString(", ")
            } else "Autor desconocido"

            val imagenes = info.optJSONObject("imageLinks")
            val imagen = imagenes?.optString("thumbnail")
                ?.takeIf { it.isNotBlank() }
                ?.replace("http://", "https://")

            val pagina = info.optString("infoLink")
                .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?.replace("http://", "https://")
                ?: item.optString("id").takeIf { it.isNotBlank() }?.let {
                    "https://books.google.com/books?id=${codificar(it)}"
                }

            resultado.add(Libro(titulo, autores, imagen, pagina))
        }
        return resultado
    }

    private fun codificar(texto: String): String = URLEncoder.encode(texto, "UTF-8")

    private fun descargar(direccion: String): String {
        val conexion = URL(direccion).openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = 8_000
            conexion.readTimeout = 8_000
            conexion.setRequestProperty("Accept", "application/json")
            conexion.setRequestProperty("User-Agent", "DigitalBooksEducativo/1.0")

            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                throw IOException("El catálogo respondió HTTP $codigo")
            }
            return conexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }
}
