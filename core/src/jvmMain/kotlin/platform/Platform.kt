package platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual fun chooseSaveFile(title: String, extension: String): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = title
    chooser.fileFilter = FileNameExtensionFilter("*.$extension", extension)
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    var file = chooser.selectedFile
    if (!file.name.endsWith(".$extension")) file = File(file.path + ".$extension")
    return file.absolutePath
}

actual fun chooseOpenFile(title: String, extension: String): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = title
    chooser.fileFilter = FileNameExtensionFilter("*.$extension", extension)
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile.absolutePath
}

actual fun writeFile(path: String, content: String) {
    File(path).writeText(content)
}

actual fun readFile(path: String): String? =
    runCatching { File(path).readText() }.getOrNull()
