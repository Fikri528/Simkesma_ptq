package id.ac.unimal.simkesma.data

data class Item(val id: String, val data: Map<String, Any?>) {
    fun str(key: String): String = data[key]?.toString().orEmpty()
    fun int(key: String): Int = (data[key] as? Number)?.toInt() ?: str(key).toIntOrNull() ?: 0
    fun long(key: String): Long = (data[key] as? Number)?.toLong() ?: str(key).toLongOrNull() ?: 0L
}

sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}
