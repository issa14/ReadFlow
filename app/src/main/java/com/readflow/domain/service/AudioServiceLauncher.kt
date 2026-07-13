package com.readflow.domain.service

/**
 * Interface pour lancer le service audio foreground.
 *
 * Permet de décorréler le ViewModel des détails Android (Context, Intent,
 * vérification de permissions). L'implémentation concrète est dans la
 * couche service.
 */
interface AudioServiceLauncher {
    /** Vérifie que toutes les conditions sont réunies pour démarrer. */
    fun canStart(): Boolean

    /** Lance le service de lecture en foreground. */
    fun start()
}
