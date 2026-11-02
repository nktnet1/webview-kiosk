package uk.nktnet.webviewkiosk

/**
 * The default HOME entry point. Android excludes HOME tasks from Recents even after
 * the user chooses a different launcher. Using a distinct component and task
 * affinity prevents ordinary [MainActivity] launches from reusing that task.
 */
class HomeActivity : MainActivity()
