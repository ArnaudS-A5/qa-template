package com.example.qa.api.sync;

/**
 * Direction d'un balayage / défilement mobile, utilisée par {@link MobileSync}.
 * Type neutre du socle (aucune dépendance Appium ne fuit dans le contrat public).
 */
public enum SwipeDirection {
	UP,
	DOWN,
	LEFT,
	RIGHT
}
