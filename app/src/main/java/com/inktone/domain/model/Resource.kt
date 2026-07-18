package com.inktone.domain.model

/**
 * Conteneur générique pour les états de chargement réseau.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val throwable: Throwable, val message: String? = null) : Resource<Nothing>()
}
