package platform

expect fun chooseSaveFile(title: String, extension: String): String?
expect fun chooseOpenFile(title: String, extension: String): String?
expect fun writeFile(path: String, content: String)
expect fun readFile(path: String): String?
