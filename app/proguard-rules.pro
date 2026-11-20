# Règles R8 propres à l'appli.
# Room, ML Kit, Compose et Coil fournissent déjà leurs propres règles : rien à ajouter pour l'instant.

# Version publiée : aucun journal (les messages contiennent des noms de fichiers).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
