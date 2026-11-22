package app.toque.recipes

import android.content.Context
import app.toque.Logbook
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.RecipeCodec
import app.toque.core.recipes.RecipeSource
import java.io.File

/**
 * Recettes déposées dans Android/data/app.toque/files/import/ :
 * - data.json, liste de recettes personnelles écrites pour un autre robot (format schema.org
 *   Recipe) : converties pour le Smart, avec leurs photos du sous-dossier images/ (r12345.jpg) ;
 * - fichier « toque-recettes-1 ».
 * Un fichier importé est renommé en .importe pour ne pas être relu à chaque ouverture ; on peut le
 * renvoyer pour mettre à jour (les recettes sont remplacées, les favoris gardés).
 */
class FileImport(private val context: Context, private val repo: RecipeRepo, private val log: Logbook) {
    class Report(val files: Int, val recipes: Int, val bad: Int, val review: Int, val busy: Boolean = false) {
        val message: String
            get() = when {
                busy -> tr("Import déjà en cours, patientez")
                files == 0 -> tr("Aucun fichier de recettes à importer")
                bad > 0 -> tr("%d recettes importées, %d illisibles", recipes, bad)
                else -> tr("%d recettes importées", recipes)
            }
    }

    companion object {
        /** Un seul import à la fois (ouverture de l'appli et bouton des réglages). */
        private val running = java.util.concurrent.atomic.AtomicBoolean(false)
        val isRunning: Boolean get() = running.get()
    }

    val dir: File? get() = try { context.getExternalFilesDir("import") } catch (_: Exception) { null }

    fun pending(): List<File> = dir?.listFiles { f -> f.isFile && f.name.endsWith(".json", true) }?.sortedBy { it.name }.orEmpty()

    fun run(): Report {
        if (!running.compareAndSet(false, true)) return Report(0, 0, 0, 0, busy = true)
        try {
            return runNow()
        } finally {
            running.set(false)
        }
    }

    private fun runNow(): Report {
        val files = pending()
        var count = 0
        var bad = 0
        var review = 0
        for (f in files) {
            try {
                val root = Json.parse(f.readText())
                val (list, wrong) = if (Cookidoo.isExport(root)) {
                    val r = Cookidoo.parseAll(root)
                    copyImages(f.parentFile, r.first.map { it.id })
                    r
                } else RecipeCodec.decodeFile(f.readText(), RecipeSource.USER)
                repo.upsertAll(list)
                count += list.size
                bad += wrong
                review += list.sumOf { r -> r.steps.count { it.needsReview } }
                f.renameTo(File(f.parentFile, f.name + ".importe"))
                log.write("import", "${f.name} : ${list.size} recettes, $wrong illisibles")
            } catch (e: Exception) {
                log.write("import", "${f.name} : ${e.message}")
                bad++
            }
        }
        return Report(files.size, count, bad, review)
    }

    /** Photos : images/r12345.jpg → dossier d'images de l'appli, sous le nom tm-r12345.jpg. */
    private fun copyImages(base: File?, ids: List<String>) {
        val src = File(base ?: return, "images")
        if (!src.isDirectory) return
        for (id in ids) {
            val from = File(src, id.removePrefix(Cookidoo.ID_PREFIX) + ".jpg")
            if (!from.isFile) continue
            try {
                from.copyTo(File(repo.imagesDir, Cookidoo.imageName(id)), overwrite = true)
            } catch (e: Exception) {
                log.write("import", "${from.name} : ${e.message}")
            }
        }
    }
}
