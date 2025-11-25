package com.example.mypdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.mypdf.Language

/**
 * Modelo de textos de la app. Añade aquí las claves que necesites.
 */
interface AppStrings {
    // General / comunes
    val close: String
    val cancel: String
    val create: String
    val delete: String
    val rename: String
    val settingsTitle: String
    val languageLabel: String
    val gridSize: String

    // Biblioteca
    val themes: String
    val categories: String
    val newCategory: String
    val newFolder: String
    val folderName: String
    val searchInLibrary: String
    val importPdf: String
    val createFolder: String
    val emptyList: String
    val searching: String
    val cannotCreateCategory: String
    val cannotCreateFolder: String
    val cannotRename: String
    val deleteError: String
    val confirmDeleteTitle: String
    fun confirmDeleteMessage(isFolder: Boolean): String

    // Ordenación
    val sortByName: String
    val sortByDate: String
    val sortBySize: String

    // Rename dialog
    val newNameLabel: String
    val save: String

    // Tuner dialogs
    val tunerSettingsTitle: String
    val tunerNoisyEnv: String
    val tunerDaltonismSoon: String
    val tunerExtendedMode: String

    // Top bar
    val backDescription: String
    val tunerDescription: String
    val concertDescription: String

    // Viewer toolbar
    val toolMove: String
    val toolPen: String
    val toolErase: String
    val toolThin: String
    val toolMedium: String
    val toolThick: String
    val toolSmooth: String
    val toolUndo: String
    
    // Onboarding
    val welcomeTitle: String
    val chooseLanguage: String
    val chooseTheme: String
    val chooseDaltonism: String


    val daltonismOption: String
    val next: String
    val onboardingDisclaimer: String
    val finish: String

    // Registration / Login
    val registrationTitle: String
    val loginTitle: String
    val username: String
    val email: String
    val password: String
    val register: String
    val login: String
    val googleSignIn: String
    val continueGuest: String
    val skip: String
    val welcomeUser: String
    val configuring: String
    val welcomeSubtitle: String
    val orSeparator: String

    // Tutorial
    val tutorialTitle: String
    val tutorialMessage: String
    val tutorialAccept: String
    val tutorialDeny: String
    
    // New Tutorial Steps
    val tutorialIntroTitle: String
    val tutorialIntroBody: String
    val tutorialNewCategoryTitle: String
    val tutorialNewCategoryBody: String
    val tutorialFabTitle: String
    val tutorialFabBody: String
    val tutorialImportTitle: String
    val tutorialImportBody: String
    val tutorialFileListTitle: String
    val tutorialFileListBody: String
    val tutorialLongPressTitle: String
    val tutorialLongPressBody: String
    val tutorialOptionsTitle: String
    val tutorialOptionsBody: String
    val tutorialRenameTitle: String
    val tutorialRenameBody: String
    val tutorialOpenFileTitle: String
    val tutorialOpenFileBody: String
    val tutorialToolboxTitle: String
    val tutorialToolboxBody: String
    val tutorialTunerButtonTitle: String
    val tutorialTunerButtonBody: String
    val tutorialTunerActiveTitle: String
    val tutorialTunerActiveBody: String
    val tutorialTunerMenuTitle: String
    val tutorialTunerMenuBody: String
    val tutorialConcertModeTitle: String
    val tutorialConcertModeBody: String
    val tutorialExitConcertTitle: String
    val tutorialExitConcertBody: String
    val tutorialFinishedTitle: String
    val tutorialFinishedBody: String
    val tutorialStart: String
    val tutorialSkip: String
    val tutorialExit: String
}

object StringsEs : AppStrings {
    override val close = "Cerrar"
    override val cancel = "Cancelar"
    override val create = "Crear"
    override val delete = "Eliminar"
    override val rename = "Renombrar"
    override val settingsTitle = "Ajustes"
    override val languageLabel = "Idioma"
    override val gridSize = "Tamaño de cuadrícula"

    override val themes = "Temas"
    override val categories = "Categorías"
    override val newCategory = "Nueva categoría"
    override val newFolder = "Nueva carpeta"
    override val folderName = "Nombre carpeta"
    override val searchInLibrary = "Buscar en biblioteca"
    override val importPdf = "Importar PDF"
    override val createFolder = "Crear carpeta"
    override val emptyList = "Sin elementos"
    override val searching = "Buscando…"
    override val cannotCreateCategory = "No se pudo crear la categoría"
    override val cannotCreateFolder = "No se pudo crear carpeta"
    override val cannotRename = "No se pudo renombrar"
    override val deleteError = "No se pudo eliminar"
    override val confirmDeleteTitle = "Eliminar"
    override fun confirmDeleteMessage(isFolder: Boolean): String =
        if (isFolder) "¿Seguro que quieres eliminar la carpeta? Esta acción no se puede deshacer."
        else "¿Seguro que quieres eliminar el PDF? Esta acción no se puede deshacer."

    override val sortByName = "Nombre"
    override val sortByDate = "Fecha"
    override val sortBySize = "Tamaño"

    override val newNameLabel = "Nuevo nombre"
    override val save = "Guardar"

    override val tunerSettingsTitle = "Ajustes del afinador"
    override val tunerNoisyEnv = "Ambiente ruidoso"
    override val tunerDaltonismSoon = "Daltonismo"
    override val tunerExtendedMode = "Modo extendido"

    override val backDescription = "Volver"
    override val tunerDescription = "Afinador"
    override val concertDescription = "Concert"

    override val toolMove = "Mover"
    override val toolPen = "Rotulador"
    override val toolErase = "Borrar"
    override val toolThin = "Fino"
    override val toolMedium = "Medio"
    override val toolThick = "Grueso"
    override val toolSmooth = "Suave"
    override val toolUndo = "Deshacer"
    
    override val welcomeTitle = "Bienvenido"
    override val chooseLanguage = "Elige tu idioma"
    override val chooseTheme = "Elige un tema"
    override val chooseDaltonism = "Modo daltónico"
    override val finish = "Finalizar"

    override val daltonismOption = "Daltonismo"
    override val next = "Siguiente"
    override val onboardingDisclaimer = "No te preocupes, puedes volver a cambiar esto en configuración."

    override val registrationTitle = "Crear cuenta"
    override val loginTitle = "Iniciar sesión"
    override val username = "Nombre de usuario"
    override val email = "Correo electrónico"
    override val password = "Contraseña"
    override val register = "Registrarse"
    override val login = "Entrar"
    override val googleSignIn = "Continuar con Google"
    override val continueGuest = "Continuar como invitado"
    override val skip = "Omitir"
    override val welcomeUser = "Bienvenido"
    override val configuring = "Estamos configurándolo todo..."
    override val welcomeSubtitle = "Tu compañero creativo"
    override val orSeparator = "o"

    override val tutorialTitle = "¿Quieres aprender a usar la app?"
    override val tutorialMessage = "Te enseñaremos lo básico en unos sencillos pasos."
    override val tutorialAccept = "Sí, enséñame"
    override val tutorialDeny = "No, gracias"
    
    override val tutorialIntroTitle = "Bienvenido al Tutorial"
    override val tutorialIntroBody = "Vamos a dar un paseo rápido por las funciones principales."
    override val tutorialNewCategoryTitle = "Crear Categoría"
    override val tutorialNewCategoryBody = "Empieza creando una categoría para organizar tus archivos."
    override val tutorialFabTitle = "Añadir Archivos"
    override val tutorialFabBody = "Usa este botón para importar PDFs o crear carpetas."
    override val tutorialImportTitle = "Importar PDF"
    override val tutorialImportBody = "Selecciona un archivo PDF de tu dispositivo."
    override val tutorialFileListTitle = "Tus Archivos"
    override val tutorialFileListBody = "Aquí aparecerán tus archivos importados."
    override val tutorialLongPressTitle = "Opciones de Archivo"
    override val tutorialLongPressBody = "Mantén pulsado un archivo para ver más opciones."
    override val tutorialOptionsTitle = "Menú de Opciones"
    override val tutorialOptionsBody = "Aquí puedes renombrar, eliminar o mover archivos."
    override val tutorialRenameTitle = "Renombrar"
    override val tutorialRenameBody = "Dale un nombre descriptivo a tu archivo."
    override val tutorialOpenFileTitle = "Abrir Archivo"
    override val tutorialOpenFileBody = "Toca el archivo para abrir el visor."
    override val tutorialToolboxTitle = "Caja de Herramientas"
    override val tutorialToolboxBody = "Aquí están tus herramientas de dibujo."
    override val tutorialTunerButtonTitle = "Afinador"
    override val tutorialTunerButtonBody = "Pulsa aquí para abrir el afinador."
    override val tutorialTunerActiveTitle = "Afinador Activo"
    override val tutorialTunerActiveBody = "Toca el afinador para configurarlo."
    override val tutorialTunerMenuTitle = "Ajustes del Afinador"
    override val tutorialTunerMenuBody = "Pulsa aquí para configurar el afinador."
    override val tutorialConcertModeTitle = "Modo Concierto"
    override val tutorialConcertModeBody = "Activa el modo concierto para desactivar la edición."
    override val tutorialExitConcertTitle = "Salir del Modo Concierto"
    override val tutorialExitConcertBody = "Pulsa el botón de inicio para volver a editar."
    override val tutorialFinishedTitle = "Tutorial Finalizado"
    override val tutorialFinishedBody = "Puedes reactivarlo desde los ajustes."
    override val tutorialStart = "Empezar"
    override val tutorialSkip = "Omitir"
    override val tutorialExit = "Salir"
}

object StringsEn : AppStrings {
    override val close = "Close"
    override val cancel = "Cancel"
    override val create = "Create"
    override val delete = "Delete"
    override val rename = "Rename"
    override val settingsTitle = "Settings"
    override val languageLabel = "Language"
    override val gridSize = "Grid Size"

    override val themes = "Themes"
    override val categories = "Categories"
    override val newCategory = "New category"
    override val newFolder = "New folder"
    override val folderName = "Folder name"
    override val searchInLibrary = "Search in library"
    override val importPdf = "Import PDF"
    override val createFolder = "Create folder"
    override val emptyList = "No items"
    override val searching = "Searching…"
    override val cannotCreateCategory = "Could not create category"
    override val cannotCreateFolder = "Could not create folder"
    override val cannotRename = "Could not rename"
    override val deleteError = "Could not delete"
    override val confirmDeleteTitle = "Delete"
    override fun confirmDeleteMessage(isFolder: Boolean): String =
        if (isFolder) "Are you sure you want to delete the folder? This action cannot be undone."
        else "Are you sure you want to delete the PDF? This action cannot be undone."

    override val sortByName = "Name"
    override val sortByDate = "Date"
    override val sortBySize = "Size"

    override val newNameLabel = "New name"
    override val save = "Save"

    override val tunerSettingsTitle = "Tuner settings"
    override val tunerNoisyEnv = "Noisy environment"
    override val tunerDaltonismSoon = "Color blindness"
    override val tunerExtendedMode = "Extended mode"

    override val backDescription = "Back"
    override val tunerDescription = "Tuner"
    override val concertDescription = "Concert"

    override val toolMove = "Move"
    override val toolPen = "Marker"
    override val toolErase = "Erase"
    override val toolThin = "Thin"
    override val toolMedium = "Medium"
    override val toolThick = "Thick"
    override val toolSmooth = "Smooth"
    override val toolUndo = "Undo"

    override val welcomeTitle = "Welcome"
    override val chooseLanguage = "Choose your language"
    override val chooseTheme = "Choose a theme"
    override val chooseDaltonism = "Color blindness mode"
    override val finish = "Finish"

    override val daltonismOption = "Color blindness"
    override val next = "Next"
    override val onboardingDisclaimer = "Don't worry, you can change this later in settings."

    override val registrationTitle = "Create account"
    override val loginTitle = "Log in"
    override val username = "Username"
    override val email = "Email"
    override val password = "Password"
    override val register = "Sign up"
    override val login = "Log in"
    override val googleSignIn = "Continue with Google"
    override val continueGuest = "Continue as Guest"
    override val skip = "Skip"
    override val welcomeUser = "Welcome"
    override val configuring = "Configuring everything..."
    override val welcomeSubtitle = "Your creative companion"
    override val orSeparator = "or"

    override val tutorialTitle = "Want to learn how to use the app?"
    override val tutorialMessage = "We'll show you the basics in a few simple steps."
    override val tutorialAccept = "Yes, show me"
    override val tutorialDeny = "No, thanks"
    
    override val tutorialIntroTitle = "Welcome to the Tutorial"
    override val tutorialIntroBody = "Let's take a quick tour of the main features."
    override val tutorialNewCategoryTitle = "Create Category"
    override val tutorialNewCategoryBody = "Start by creating a category to organize your files."
    override val tutorialFabTitle = "Add Files"
    override val tutorialFabBody = "Use this button to import PDFs or create folders."
    override val tutorialImportTitle = "Import PDF"
    override val tutorialImportBody = "Select a PDF file from your device."
    override val tutorialFileListTitle = "Your Files"
    override val tutorialFileListBody = "Your imported files will appear here."
    override val tutorialLongPressTitle = "File Options"
    override val tutorialLongPressBody = "Long press a file to see more options."
    override val tutorialOptionsTitle = "Options Menu"
    override val tutorialOptionsBody = "Here you can rename, delete, or move files."
    override val tutorialRenameTitle = "Rename"
    override val tutorialRenameBody = "Give your file a descriptive name."
    override val tutorialOpenFileTitle = "Open File"
    override val tutorialOpenFileBody = "Tap the file to open the viewer."
    override val tutorialToolboxTitle = "Toolbox"
    override val tutorialToolboxBody = "Here are your drawing tools."
    override val tutorialTunerButtonTitle = "Tuner"
    override val tutorialTunerButtonBody = "Click here to open the tuner."
    override val tutorialTunerActiveTitle = "Tuner Active"
    override val tutorialTunerActiveBody = "Tap the tuner to configure it."
    override val tutorialTunerMenuTitle = "Tuner Settings"
    override val tutorialTunerMenuBody = "Click here to configure the tuner."
    override val tutorialConcertModeTitle = "Concert Mode"
    override val tutorialConcertModeBody = "Enable concert mode to disable editing."
    override val tutorialExitConcertTitle = "Exit Concert Mode"
    override val tutorialExitConcertBody = "Tap the home button to return to editing."
    override val tutorialFinishedTitle = "Tutorial Finished"
    override val tutorialFinishedBody = "You can reactivate it from settings."
    override val tutorialStart = "Start"
    override val tutorialSkip = "Skip"
    override val tutorialExit = "Exit"
}

object StringsFr : AppStrings {
    override val close = "Fermer"
    override val cancel = "Annuler"
    override val create = "Créer"
    override val delete = "Supprimer"
    override val rename = "Renommer"
    override val settingsTitle = "Paramètres"
    override val languageLabel = "Langue"
    override val gridSize = "Taille de la grille"

    override val themes = "Thèmes"
    override val categories = "Catégories"
    override val newCategory = "Nouvelle catégorie"
    override val newFolder = "Nouveau dossier"
    override val folderName = "Nom du dossier"
    override val searchInLibrary = "Rechercher dans la bibliothèque"
    override val importPdf = "Importer PDF"
    override val createFolder = "Créer un dossier"
    override val emptyList = "Aucun élément"
    override val searching = "Recherche…"
    override val cannotCreateCategory = "Impossible de créer la catégorie"
    override val cannotCreateFolder = "Impossible de créer le dossier"
    override val cannotRename = "Impossible de renommer"
    override val deleteError = "Impossible de supprimer"
    override val confirmDeleteTitle = "Supprimer"
    override fun confirmDeleteMessage(isFolder: Boolean): String =
        if (isFolder) "Voulez-vous vraiment supprimer le dossier ? Cette action est irréversible."
        else "Voulez-vous vraiment supprimer le PDF ? Cette action est irréversible."

    override val sortByName = "Nom"
    override val sortByDate = "Date"
    override val sortBySize = "Taille"

    override val newNameLabel = "Nouveau nom"
    override val save = "Enregistrer"

    override val tunerSettingsTitle = "Réglages de l'accordeur"
    override val tunerNoisyEnv = "Environnement bruyant"
    override val tunerDaltonismSoon = "Daltonisme"
    override val tunerExtendedMode = "Mode étendu"

    override val backDescription = "Retour"
    override val tunerDescription = "Accordeur"
    override val concertDescription = "Concert"

    override val toolMove = "Déplacer"
    override val toolPen = "Marqueur"
    override val toolErase = "Gomme"
    override val toolThin = "Fin"
    override val toolMedium = "Moyen"
    override val toolThick = "Épais"
    override val toolSmooth = "Lisser"
    override val toolUndo = "Annuler"

    override val welcomeTitle = "Bienvenue"
    override val chooseLanguage = "Choisissez votre langue"
    override val chooseTheme = "Choisissez un thème"
    override val chooseDaltonism = "Mode daltonien"
    override val finish = "Terminer"

    override val daltonismOption = "Daltonisme"
    override val next = "Suivant"
    override val onboardingDisclaimer = "Ne vous inquiétez pas, vous pourrez changer cela plus tard dans les paramètres."

    override val registrationTitle = "Créer un compte"
    override val loginTitle = "Connexion"
    override val username = "Nom d'utilisateur"
    override val email = "E-mail"
    override val password = "Mot de passe"
    override val register = "S'inscrire"
    override val login = "Se connecter"
    override val googleSignIn = "Continuer avec Google"
    override val continueGuest = "Continuer en tant qu'invité"
    override val skip = "Passer"
    override val welcomeUser = "Bienvenue"
    override val configuring = "Configuration en cours..."
    override val welcomeSubtitle = "Votre compagnon créatif"
    override val orSeparator = "ou"

    override val tutorialTitle = "Voulez-vous apprendre à utiliser l'application ?"
    override val tutorialMessage = "Nous allons vous montrer les bases en quelques étapes simples."
    override val tutorialAccept = "Oui, montrez-moi"
    override val tutorialDeny = "Non, merci"

    override val tutorialIntroTitle = "Bienvenue dans le tutoriel"
    override val tutorialIntroBody = "Faisons un tour rapide des fonctionnalités principales."
    override val tutorialNewCategoryTitle = "Créer une catégorie"
    override val tutorialNewCategoryBody = "Commencez par créer une catégorie pour organiser vos fichiers."
    override val tutorialFabTitle = "Ajouter des fichiers"
    override val tutorialFabBody = "Utilisez ce bouton pour importer des PDF ou créer des dossiers."
    override val tutorialImportTitle = "Importer PDF"
    override val tutorialImportBody = "Sélectionnez un fichier PDF sur votre appareil."
    override val tutorialFileListTitle = "Vos fichiers"
    override val tutorialFileListBody = "Vos fichiers importés apparaîtront ici."
    override val tutorialLongPressTitle = "Options de fichier"
    override val tutorialLongPressBody = "Appuyez longuement sur un fichier pour voir plus d'options."
    override val tutorialOptionsTitle = "Menu d'options"
    override val tutorialOptionsBody = "Ici, vous pouvez renommer, supprimer ou déplacer des fichiers."
    override val tutorialRenameTitle = "Renommer"
    override val tutorialRenameBody = "Donnez un nom descriptif à votre fichier."
    override val tutorialOpenFileTitle = "Ouvrir le fichier"
    override val tutorialOpenFileBody = "Appuyez sur le fichier pour ouvrir la visionneuse."
    override val tutorialToolboxTitle = "Boîte à outils"
    override val tutorialToolboxBody = "Voici vos outils de dessin."
    override val tutorialTunerButtonTitle = "Accordeur"
    override val tutorialTunerButtonBody = "Cliquez ici pour ouvrir l'accordeur."
    override val tutorialTunerActiveTitle = "Accordeur Actif"
    override val tutorialTunerActiveBody = "Appuyez sur l'accordeur pour le configurer."
    override val tutorialTunerMenuTitle = "Paramètres de l'accordeur"
    override val tutorialTunerMenuBody = "Cliquez ici pour configurer l'accordeur."
    override val tutorialConcertModeTitle = "Mode Concert"
    override val tutorialConcertModeBody = "Activez le mode concert pour désactiver l'édition."
    override val tutorialExitConcertTitle = "Quitter le Mode Concert"
    override val tutorialExitConcertBody = "Appuyez sur le bouton d'accueil pour revenir à l'édition."
    override val tutorialFinishedTitle = "Tutoriel Terminé"
    override val tutorialFinishedBody = "Vous pouvez le réactiver depuis les paramètres."
    override val tutorialStart = "Commencer"
    override val tutorialSkip = "Passer"
    override val tutorialExit = "Quitter"
}

object StringsIt : AppStrings {
    override val close = "Chiudi"
    override val cancel = "Annulla"
    override val create = "Crea"
    override val delete = "Elimina"
    override val rename = "Rinomina"
    override val settingsTitle = "Impostazioni"
    override val languageLabel = "Lingua"
    override val gridSize = "Dimensione griglia"

    override val themes = "Temi"
    override val categories = "Categorie"
    override val newCategory = "Nuova categoria"
    override val newFolder = "Nuova cartella"
    override val folderName = "Nome cartella"
    override val searchInLibrary = "Cerca nella libreria"
    override val importPdf = "Importa PDF"
    override val createFolder = "Crea cartella"
    override val emptyList = "Nessun elemento"
    override val searching = "Ricerca…"
    override val cannotCreateCategory = "Impossibile creare la categoria"
    override val cannotCreateFolder = "Impossibile creare la cartella"
    override val cannotRename = "Impossibile rinominare"
    override val deleteError = "Impossibile eliminare"
    override val confirmDeleteTitle = "Elimina"
    override fun confirmDeleteMessage(isFolder: Boolean): String =
        if (isFolder) "Sei sicuro di voler eliminare la cartella? Questa azione non può essere annullata."
        else "Sei sicuro di voler eliminare il PDF? Questa azione non può essere annullata."

    override val sortByName = "Nome"
    override val sortByDate = "Data"
    override val sortBySize = "Dimensione"

    override val newNameLabel = "Nuovo nome"
    override val save = "Salva"

    override val tunerSettingsTitle = "Impostazioni accordatore"
    override val tunerNoisyEnv = "Ambiente rumoroso"
    override val tunerDaltonismSoon = "Daltonismo"
    override val tunerExtendedMode = "Modalità estesa"

    override val backDescription = "Indietro"
    override val tunerDescription = "Accordatore"
    override val concertDescription = "Concerto"

    override val toolMove = "Sposta"
    override val toolPen = "Pennarello"
    override val toolErase = "Gomma"
    override val toolThin = "Sottile"
    override val toolMedium = "Medio"
    override val toolThick = "Spesso"
    override val toolSmooth = "Liscio"
    override val toolUndo = "Annulla"

    override val welcomeTitle = "Benvenuto"
    override val chooseLanguage = "Scegli la tua lingua"
    override val chooseTheme = "Scegli un tema"
    override val chooseDaltonism = "Modalità daltonici"
    override val finish = "Fine"

    override val daltonismOption = "Daltonismo"
    override val next = "Avanti"
    override val onboardingDisclaimer = "Non preoccuparti, puoi cambiarlo più tardi nelle impostazioni."

    override val registrationTitle = "Crea account"
    override val loginTitle = "Accedi"
    override val username = "Nome utente"
    override val email = "Email"
    override val password = "Password"
    override val register = "Registrati"
    override val login = "Accedi"
    override val googleSignIn = "Continua con Google"
    override val continueGuest = "Continua come ospite"
    override val skip = "Salta"
    override val welcomeUser = "Benvenuto"
    override val configuring = "Stiamo configurando tutto..."
    override val welcomeSubtitle = "Il tuo compagno creativo"
    override val orSeparator = "o"

    override val tutorialTitle = "Vuoi imparare a usare l'app?"
    override val tutorialMessage = "Ti mostreremo le basi in pochi semplici passaggi."
    override val tutorialAccept = "Sì, mostrami"
    override val tutorialDeny = "No, grazie"

    override val tutorialIntroTitle = "Benvenuto nel Tutorial"
    override val tutorialIntroBody = "Facciamo un rapido tour delle funzionalità principali."
    override val tutorialNewCategoryTitle = "Crea Categoria"
    override val tutorialNewCategoryBody = "Inizia creando una categoria per organizzare i tuoi file."
    override val tutorialFabTitle = "Aggiungi File"
    override val tutorialFabBody = "Usa questo pulsante per importare PDF o creare cartelle."
    override val tutorialImportTitle = "Importa PDF"
    override val tutorialImportBody = "Seleziona un file PDF dal tuo dispositivo."
    override val tutorialFileListTitle = "I tuoi File"
    override val tutorialFileListBody = "I tuoi file importati appariranno qui."
    override val tutorialLongPressTitle = "Opzioni File"
    override val tutorialLongPressBody = "Tieni premuto un file per vedere più opzioni."
    override val tutorialOptionsTitle = "Menu Opzioni"
    override val tutorialOptionsBody = "Qui puoi rinominare, eliminare o spostare i file."
    override val tutorialRenameTitle = "Rinomina"
    override val tutorialRenameBody = "Dai un nome descrittivo al tuo file."
    override val tutorialOpenFileTitle = "Apri File"
    override val tutorialOpenFileBody = "Tocca il file per aprire il visualizzatore."
    override val tutorialToolboxTitle = "Cassetta degli attrezzi"
    override val tutorialToolboxBody = "Ecco i tuoi strumenti di disegno."
    override val tutorialTunerButtonTitle = "Accordatore"
    override val tutorialTunerButtonBody = "Clicca qui per aprire l'accordatore."
    override val tutorialTunerActiveTitle = "Accordatore Attivo"
    override val tutorialTunerActiveBody = "Tocca l'accordatore per configurarlo."
    override val tutorialTunerMenuTitle = "Impostazioni Accordatore"
    override val tutorialTunerMenuBody = "Clicca qui per configurare l'accordatore."
    override val tutorialConcertModeTitle = "Modalità Concerto"
    override val tutorialConcertModeBody = "Attiva la modalità concerto per disabilitare la modifica."
    override val tutorialExitConcertTitle = "Esci dalla Modalità Concerto"
    override val tutorialExitConcertBody = "Tocca il pulsante home per tornare alla modifica."
    override val tutorialFinishedTitle = "Tutorial Completato"
    override val tutorialFinishedBody = "Puoi riattivarlo dalle impostazioni."
    override val tutorialStart = "Inizia"
    override val tutorialSkip = "Salta"
    override val tutorialExit = "Esci"
}

fun stringsFor(language: Language): AppStrings {
    return when (language) {
        Language.EN -> StringsEn
        Language.ES -> StringsEs
        Language.FR -> StringsFr
        Language.IT -> StringsIt
    }
}

val LocalStrings = staticCompositionLocalOf<AppStrings> {
    error("AppStrings not provided")
}

@Composable
fun ProvideStrings(language: Language, content: @Composable () -> Unit) {
    val strings = stringsFor(language)
    androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

@Composable
fun strings(): AppStrings = LocalStrings.current
