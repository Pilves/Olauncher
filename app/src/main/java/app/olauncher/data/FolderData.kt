package app.olauncher.data

data class FolderApp(
    val appName: String,
    val packageName: String,
    val activityClassName: String,
    val userString: String
)

data class FolderGroup(
    val name: String,
    val apps: List<FolderApp>
)
