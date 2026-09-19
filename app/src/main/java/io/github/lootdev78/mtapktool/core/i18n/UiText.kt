package io.github.lootdev78.mtapktool.core.i18n

import java.util.Locale

/**
 * Central DE/EN bridge. New UI should use [t]; [auto] also translates legacy literal labels
 * so older Apktool/file-manager dialogs no longer mix German and English.
 */
object UiText {
    val isGerman: Boolean get() = Locale.getDefault().language.equals("de", ignoreCase = true)
    fun t(en: String, de: String): String = if (isGerman) de else en

    private val pairs = listOf(
        "Settings" to "Einstellungen",
        "Startup" to "Start",
        "Left pane start path" to "Startpfad – linkes Fenster",
        "Right pane start path" to "Startpfad – rechtes Fenster",
        "Appearance" to "Darstellung",
        "Theme" to "Theme",
        "Theme color" to "Theme-Farbe",
        "Select the application color scheme." to "Farbschema der Oberfläche auswählen.",
        "File list size" to "Dateilisten-Größe",
        "Max lines of file name" to "Maximale Zeilen des Dateinamens",
        "File list time preference" to "Zeitdarstellung der Dateiliste",
        "Disable permission in file list" to "Dateirechte in Dateiliste ausblenden",
        "Do not show file permissions in the list." to "Dateirechte in der Liste nicht anzeigen.",
        "Date time format" to "Datums-/Zeitformat",
        "General" to "Allgemein",
        "Generate backup file" to "Backup-Datei erzeugen",
        "Create a .bak copy before saving in the text editor." to "Beim Speichern im Texteditor die Originaldatei als .bak sichern.",
        "Preserve file time" to "Dateizeit erhalten",
        "Preserve modification time when copying/extracting where possible." to "Änderungszeit beim Kopieren/Extrahieren möglichst erhalten.",
        "Sort file menu" to "Dateimenü sortieren",
        "Sort after long-pressing a file." to "Nach langem Drücken sortieren.",
        "Sort built-in opening method" to "Interne Öffnungsmethoden sortieren",
        "MTApktool built-in opening methods only." to "Nur MTApktool-eigene Öffnungsmethoden.",
        "Custom MTApktool directory" to "Benutzerdefiniertes MTApktool-Verzeichnis",
        "Recycle Bin" to "Papierkorb",
        "Enable recycle bin feature" to "Papierkorb aktivieren",
        "Deleted local files can first be moved to the recycle bin." to "Gelöschte lokale Dateien können zuerst in den Papierkorb verschoben werden.",
        "Move to recycle bin by default" to "Standardmäßig in Papierkorb verschieben",
        "When deleting, move to the recycle bin by default." to "Beim Löschen standardmäßig in den Papierkorb verschieben.",
        "Automatically clean recycle bin files" to "Papierkorb automatisch leeren",
        "Disable" to "Deaktiviert",
        "Show deletion warning" to "Löschwarnung anzeigen",
        "Show a warning before permanent deletion." to "Warnung vor endgültigem Löschen anzeigen.",
        "Installation" to "Installation",
        "APK installation verification" to "APK-Installationsprüfung",
        "Verify signature and version code before installation." to "Signatur und Versionscode vor Installation prüfen.",
        "External storage" to "Externer Speicher",
        "Load thumbnails from external storage" to "Thumbnails von externem Speicher laden",
        "Load thumbnails on SAF/USB storage." to "Thumbnails auf SAF/USB-Speichern laden.",
        "Optimize external storage data transfer" to "Datentransfer für externen Speicher optimieren",
        "Use larger buffers for copy/move on SAF/USB." to "Größere Puffer für Kopieren/Verschieben auf SAF/USB verwenden.",
        "System" to "System",
        "Light" to "Hell",
        "Dark" to "Dunkel",
        "Small" to "Klein",
        "Medium" to "Mittel",
        "Big" to "Groß",
        "Home" to "Startverzeichnis",
        "Last path" to "Letzter Pfad",
        "Show full date and seconds" to "Volles Datum und Sekunden anzeigen",
        "Hide seconds, simplified year" to "Sekunden ausblenden, Jahr verkürzen",
        "Compress entries separately" to "Einträge einzeln komprimieren",
        "Delete source files after success" to "Quelldateien nach Erfolg löschen",
        "Compress to the other pane" to "In das andere Panel komprimieren",
        "FAQ / Credits" to "FAQ / Credits",
        "DETAILS" to "DETAILS",
        "Clear finished tasks" to "Fertige Aufgaben leeren",
        "No task information yet" to "Noch keine Aufgabeninformationen",
        "active" to "aktiv",
        "Preferences" to "Einstellungen",
        "Search settings" to "Einstellungen durchsuchen",
        "Application" to "Anwendung",
        "About" to "Über",
        "Back" to "Zurück",
        "Close" to "Schließen",
        "CLOSE" to "SCHLIESSEN",
        "Cancel" to "Abbrechen",
        "CANCEL" to "ABBRECHEN",
        "Save" to "Speichern",
        "SAVE" to "SPEICHERN",
        "Delete" to "Löschen",
        "DELETE" to "LÖSCHEN",
        "Remove" to "Entfernen",
        "REMOVE" to "ENTFERNEN",
        "Rename" to "Umbenennen",
        "Create" to "Erstellen",
        "Create New" to "Neu erstellen",
        "File" to "Datei",
        "Folder" to "Ordner",
        "Name" to "Name",
        "Tools" to "Tools",
        "Sort" to "Sortieren",
        "Filter" to "Filter",
        "Search files..." to "Dateien suchen…",
        "Select all" to "Alles auswählen",
        "Apply to all" to "Auf alle anwenden",
        "Open with..." to "Öffnen mit…",
        "Open Dual File Manager" to "Dual-Dateimanager öffnen",
        "File Info" to "Dateiinformation",
        "File already exist" to "Datei existiert bereits",
        "KEEP BOTH" to "BEIDE BEHALTEN",
        "OVERWRITE" to "ÜBERSCHREIBEN",
        "SKIP" to "ÜBERSPRINGEN",
        "PASTE" to "EINFÜGEN",
        "Jump to path" to "Zu Pfad springen",
        "Installed Apps" to "Installierte Apps",
        "Search apps" to "Apps suchen",
        "Launch" to "Starten",
        "Uninstall" to "Deinstallieren",
        "Details" to "Details",
        "Other" to "Andere",
        "Enable signature verification" to "Signaturprüfung aktivieren",
        "Name pattern" to "Namensmuster",
        "APK storage path" to "APK-Speicherpfad",
        "EXTRACT APK" to "APK EXTRAHIEREN",
        "MORE" to "MEHR",
        "PREV" to "ZURÜCK",
        "NEXT" to "WEITER",
        "Find" to "Suchen",
        "Word Wrap" to "Zeilenumbruch",
        "Match case" to "Groß-/Kleinschreibung",
        "Regex" to "Regex",
        "Signature information" to "Signaturinformationen",
        "Certificate data" to "Zertifikatsdaten",
        "Add colon" to "Doppelpunkte hinzufügen",
        "Upper case" to "Großbuchstaben",
        "VIEW DATA" to "DATEN ANZEIGEN",
        "COMPARE" to "VERGLEICHEN",
        "FUNKTIONEN" to "FUNKTIONEN",
        "Tasks" to "Aufgaben",
        "STOP ALL" to "ALLE STOPPEN",
        "STOP" to "STOPPEN",
        "HIDE" to "VERSTECKEN",
        "Archive changed" to "Archiv geändert",
        "Update archive?" to "Archiv aktualisieren?",
        "Update archive" to "Archiv aktualisieren",
        "UPDATE" to "AKTUALISIEREN",
        "LATER" to "SPÄTER",
        "Compress" to "Komprimieren",
        "Extract" to "Entpacken",
        "Create archive" to "Archiv erstellen",
        "Filename" to "Dateiname",
        "Password (if required)" to "Passwort (falls erforderlich)",
        "Format" to "Format",
        "Level" to "Stufe",
        "Bookmarks" to "Lesezeichen",
        "Add group" to "Gruppe hinzufügen",
        "Rename group" to "Gruppe umbenennen",
        "Delete group" to "Gruppe löschen",
        "No bookmarks" to "Keine Lesezeichen",
        "Move to group" to "In Gruppe verschieben",
        "Move up" to "Nach oben",
        "Move down" to "Nach unten",
        "Open in other pane" to "Im anderen Fenster öffnen",
        "Add to bookmarks" to "Zu Lesezeichen hinzufügen",
        "Copy" to "Kopieren",
        "Move" to "Verschieben",
        "Done" to "Fertig",
        "More" to "Mehr",
        "Properties" to "Eigenschaften",
        "Share" to "Teilen",
        "Text editor" to "Texteditor",
        "Image viewer" to "Bildbetrachter",
        "Audio player" to "Audioplayer",
        "Video player" to "Videoplayer",
        "Open archive" to "Archiv öffnen",
        "APK information" to "APK-Informationen",
        "Runtime" to "Laufzeit",
        "Keystore" to "Keystore",
        "Projects root" to "Projekt-Stammordner",
        "Build output root" to "Build-Ausgabeordner",
        "Command" to "Befehl",
        "Tag (optional)" to "Tag (optional)",
        "Custom AAPT2" to "Benutzerdefiniertes AAPT2",
        "Import framework" to "Framework importieren",
        "Framework importieren" to "Framework importieren",
        "Dekompilieren" to "Dekompilieren",
        "Erstellen & Dekodieren" to "Erstellen & Dekodieren",
        "Signatur" to "Signatur",
        "Archivierung" to "Archivierung",
        "Papierkorb leeren" to "Papierkorb leeren",
        "Versteckte Dateien" to "Versteckte Dateien",
        "Sortieren" to "Sortieren",
        "Speicherort" to "Speicherort",
        "In Papierkorb verschieben" to "In Papierkorb verschieben",
        "Dieses Projekt kompilieren" to "Dieses Projekt kompilieren",
        "Lesezeichen" to "Lesezeichen",
        "Lokalen Speicher hinzufügen" to "Lokalen Speicher hinzufügen",
        "Speicher hinzufügen" to "Speicher hinzufügen",
        "Manuell versteckte Dateien" to "Manuell versteckte Dateien",
        "ALLE EINBLENDEN" to "ALLE EINBLENDEN",
        "FERTIG" to "FERTIG",
        "RESET" to "ZURÜCKSETZEN",
        "ZURÜCKSETZEN" to "ZURÜCKSETZEN",
        "ZURÜCK" to "ZURÜCK",
        "SPEICHERN" to "SPEICHERN",
        "ABBRECHEN" to "ABBRECHEN",
        "SCHLIESSEN" to "SCHLIESSEN",
        "LÖSCHEN" to "LÖSCHEN",
        "ANZEIGEN" to "ANZEIGEN",
        "ENTFERNEN" to "ENTFERNEN"
    )
    private val enToDe = pairs.toMap()
    private val deToEn = pairs.associate { (en, de) -> de to en }

    fun auto(text: String): String {
        if (!isGerman) return deToEn[text] ?: text
        enToDe[text]?.let { return it }
        return when {
            text.startsWith("Copied ") -> text.replaceFirst("Copied ", "Kopiert: ").replace(" item(s)", " Element(e)")
            text.startsWith("Moved ") -> text.replaceFirst("Moved ", "Verschoben: ").replace(" item(s)", " Element(e)")
            text.startsWith("Created ") -> text.replaceFirst("Created ", "Erstellt: ")
            text.startsWith("Opened ") -> text.replaceFirst("Opened ", "Geöffnet: ")
            text.startsWith("Updated ") -> text.replaceFirst("Updated ", "Aktualisiert: ")
            text.startsWith("Saved ") -> text.replaceFirst("Saved ", "Gespeichert: ")
            text.startsWith("Extracted to ") -> text.replaceFirst("Extracted to ", "Entpackt nach ")
            text.startsWith("Copy failed:") -> text.replaceFirst("Copy failed:", "Kopieren fehlgeschlagen:")
            text.startsWith("Move failed:") -> text.replaceFirst("Move failed:", "Verschieben fehlgeschlagen:")
            text.startsWith("Delete failed:") -> text.replaceFirst("Delete failed:", "Löschen fehlgeschlagen:")
            text.startsWith("Rename failed:") -> text.replaceFirst("Rename failed:", "Umbenennen fehlgeschlagen:")
            text.startsWith("Compression failed:") -> text.replaceFirst("Compression failed:", "Komprimieren fehlgeschlagen:")
            text.startsWith("Extraction failed:") -> text.replaceFirst("Extraction failed:", "Entpacken fehlgeschlagen:")
            text.startsWith("Archive open failed:") -> text.replaceFirst("Archive open failed:", "Archiv konnte nicht geöffnet werden:")
            text.startsWith("Archive save failed:") -> text.replaceFirst("Archive save failed:", "Archiv konnte nicht gespeichert werden:")
            text.startsWith("Archive update failed:") -> text.replaceFirst("Archive update failed:", "Archivaktualisierung fehlgeschlagen:")
            text.startsWith("Link failed:") -> text.replaceFirst("Link failed:", "Verknüpfung fehlgeschlagen:")
            text.startsWith("Path not found:") -> text.replaceFirst("Path not found:", "Pfad nicht gefunden:")
            text.startsWith("Cannot open path:") -> text.replaceFirst("Cannot open path:", "Pfad kann nicht geöffnet werden:")
            text.endsWith(" item(s) hidden") -> text.replace(" item(s) hidden", " Element(e) ausgeblendet")
            " is read-only; " in text -> text.replace(" is read-only; ", " ist schreibgeschützt; ")
            else -> text
        }
    }
}
