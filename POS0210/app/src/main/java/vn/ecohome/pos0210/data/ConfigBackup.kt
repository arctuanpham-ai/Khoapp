package vn.ecohome.pos0210.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ConfigBackup {
    private const val CONFIG_VERSION = 1
    private const val MASTER_NAME = "POS0210_MASTER.0210"
    private const val MASTER_PATH = "Download/POS0210/"

    fun exportConfig(context: Context, uri: Uri): Result<Unit> = runCatching {
        val db = PosDatabase.get(context)
        val dao = db.dao()
        val snapshot = runBlocking {
            ConfigSnapshot(
                dao.allAreasSnapshot(),
                dao.allTablesSnapshot(),
                dao.allCategoriesSnapshot(),
                dao.allMenuSnapshot(),
                dao.allEmployeesSnapshot(),
                dao.allPurchaseCategoriesSnapshot(),
                dao.allSettingsSnapshot()
            )
        }

        val root = JSONObject()
        root.put("format", "POS0210_MASTER_CONFIG")
        root.put("version", CONFIG_VERSION)
        root.put("createdAt", System.currentTimeMillis())

        root.put("areas", JSONArray().apply {
            snapshot.areas.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("name", a.name); put("sortOrder", a.sortOrder); put("active", a.active)
                })
            }
        })
        root.put("tables", JSONArray().apply {
            snapshot.tables.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("areaId", t.areaId); put("name", t.name)
                    put("sortOrder", t.sortOrder); put("active", t.active)
                })
            }
        })
        root.put("categories", JSONArray().apply {
            snapshot.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("sortOrder", c.sortOrder); put("active", c.active)
                })
            }
        })

        val menuArray = JSONArray()
        val imageEntries = mutableMapOf<String, Uri>()
        snapshot.menu.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id); put("categoryId", m.categoryId); put("name", m.name); put("price", m.price)
                put("sortOrder", m.sortOrder); put("active", m.active)
            }
            if (!m.imageUri.isNullOrBlank()) {
                val imageUri = Uri.parse(m.imageUri)
                val ext = imageUri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..5 } ?: "jpg"
                val entry = "images/menu_${m.id}.$ext"
                obj.put("imageEntry", entry)
                imageEntries[entry] = imageUri
            }
            menuArray.put(obj)
        }
        root.put("menu", menuArray)

        root.put("employees", JSONArray().apply {
            snapshot.employees.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("active", e.active); put("pin", e.pin); put("role", e.role)
                    put("canCheckout", e.canCheckout); put("canPurchase", e.canPurchase); put("canOrder", e.canOrder)
                    put("canSendKitchen", e.canSendKitchen); put("canViewReport", e.canViewReport)
                    put("canManageMenu", e.canManageMenu); put("canManageSystem", e.canManageSystem)
                })
            }
        })
        root.put("purchaseCategories", JSONArray().apply {
            snapshot.purchaseCategories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("defaultUnit", c.defaultUnit)
                    put("sortOrder", c.sortOrder); put("active", c.active)
                })
            }
        })
        root.put("settings", JSONArray().apply {
            snapshot.settings
                .filterNot { it.key == "autoback_tree_uri" }
                .forEach { s -> put(JSONObject().apply { put("key", s.key); put("value", s.value) }) }
        })

        context.contentResolver.openOutputStream(uri, "w").use { raw ->
            requireNotNull(raw)
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("config.json"))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                imageEntries.forEach { (entry, imageUri) ->
                    runCatching {
                        context.contentResolver.openInputStream(imageUri)?.use { input ->
                            zip.putNextEntry(ZipEntry(entry))
                            input.copyTo(zip)
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }

    fun saveMasterToDownloads(context: Context): Result<Uri> = runCatching {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Android này chưa hỗ trợ auto MASTER Downloads" }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?"
        val args = arrayOf(MASTER_NAME, MASTER_PATH)
        var target: Uri? = null
        resolver.query(collection, projection, selection, args, MediaStore.Downloads.DATE_MODIFIED + " DESC")?.use { c ->
            if (c.moveToFirst()) target = Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        val outUri = target ?: resolver.insert(
            collection,
            android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, MASTER_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, MASTER_PATH)
            }
        ) ?: error("Không tạo được MASTER chuẩn")
        exportConfig(context, outUri).getOrThrow()
        outUri
    }

    fun copyMasterToDownloads(context: Context, source: Uri): Result<Uri> = runCatching {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Android này chưa hỗ trợ MASTER Downloads" }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?"
        val args = arrayOf(MASTER_NAME, MASTER_PATH)
        var target: Uri? = null
        resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, MediaStore.Downloads.DATE_MODIFIED + " DESC")?.use { c ->
            if (c.moveToFirst()) target = Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        val outUri = target ?: resolver.insert(
            collection,
            android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, MASTER_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, MASTER_PATH)
            }
        ) ?: error("Không tạo được MASTER chuẩn")
        resolver.openInputStream(source).use { input ->
            requireNotNull(input)
            resolver.openOutputStream(outUri, "w").use { output ->
                requireNotNull(output)
                input.copyTo(output)
            }
        }
        outUri
    }

    fun findMasterInDownloads(context: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?"
        val args = arrayOf(MASTER_NAME, MASTER_PATH)
        resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, MediaStore.Downloads.DATE_MODIFIED + " DESC")?.use { c ->
            if (c.moveToFirst()) return Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        return null
    }

    fun autoImportMasterFromDownloads(context: Context): Result<Boolean> = runCatching {
        val uri = findMasterInDownloads(context) ?: return@runCatching false
        importConfig(context, uri).getOrThrow()
        true
    }

    fun importConfig(context: Context, uri: Uri): Result<Unit> = runCatching {
        val imageDir = File(context.filesDir, "config_images").apply { mkdirs() }
        var jsonText: String? = null
        val extracted = mutableMapOf<String, File>()

        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw)
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "config.json") {
                        jsonText = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (entry.name.startsWith("images/")) {
                        val safeName = entry.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                        val out = File(imageDir, safeName)
                        out.outputStream().use { zip.copyTo(it) }
                        extracted[entry.name] = out
                    }
                    zip.closeEntry()
                }
            }
        }

        val root = JSONObject(requireNotNull(jsonText) { "File .0210 không hợp lệ: thiếu config.json" })
        require(root.optString("format") == "POS0210_MASTER_CONFIG") { "Không đúng file cấu hình 0210" }
        require(root.optInt("version", 0) in 1..CONFIG_VERSION) { "Phiên bản cấu hình chưa được hỗ trợ" }

        val db = PosDatabase.get(context)
        val dao = db.dao()
        runBlocking {
            db.withTransaction {
                root.getJSONArray("areas").forEachObject { o ->
                    dao.saveArea(AreaEntity(o.getString("id"), o.getString("name"), o.optInt("sortOrder"), o.optBoolean("active", true)))
                }
                root.getJSONArray("tables").forEachObject { o ->
                    dao.saveTable(DiningTableEntity(o.getString("id"), o.getString("areaId"), o.getString("name"), o.optInt("sortOrder"), o.optBoolean("active", true)))
                }
                root.getJSONArray("categories").forEachObject { o ->
                    dao.saveCategory(MenuCategoryEntity(o.getString("id"), o.getString("name"), o.optInt("sortOrder"), o.optBoolean("active", true)))
                }
                root.getJSONArray("menu").forEachObject { o ->
                    val entry = o.optString("imageEntry", "")
                    val localUri = extracted[entry]?.let { Uri.fromFile(it).toString() }
                    dao.saveMenuItem(
                        MenuItemEntity(
                            id = o.getString("id"),
                            categoryId = o.getString("categoryId"),
                            name = o.getString("name"),
                            price = o.getLong("price"),
                            imageUri = localUri,
                            sortOrder = o.optInt("sortOrder"),
                            active = o.optBoolean("active", true)
                        )
                    )
                }
                root.getJSONArray("employees").forEachObject { o ->
                    dao.saveEmployee(
                        EmployeeEntity(
                            id = o.getString("id"), name = o.getString("name"), active = o.optBoolean("active", true),
                            pin = o.optString("pin", "0000"), role = o.optString("role", "STAFF"),
                            canCheckout = o.optBoolean("canCheckout", true),
                            canPurchase = o.optBoolean("canPurchase", false),
                            canOrder = o.optBoolean("canOrder", true),
                            canSendKitchen = o.optBoolean("canSendKitchen", true),
                            canViewReport = o.optBoolean("canViewReport", false),
                            canManageMenu = o.optBoolean("canManageMenu", false),
                            canManageSystem = o.optBoolean("canManageSystem", false)
                        )
                    )
                }
                root.optJSONArray("purchaseCategories")?.forEachObject { o ->
                    dao.savePurchaseCategory(
                        PurchaseCategoryEntity(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            defaultUnit = o.optString("defaultUnit", "lần"),
                            sortOrder = o.optInt("sortOrder"),
                            active = o.optBoolean("active", true)
                        )
                    )
                }
                root.getJSONArray("settings").forEachObject { o ->
                    dao.saveSetting(AppSettingEntity(o.getString("key"), o.optString("value", "")))
                }
            }
        }
    }

    private data class ConfigSnapshot(
        val areas: List<AreaEntity>,
        val tables: List<DiningTableEntity>,
        val categories: List<MenuCategoryEntity>,
        val menu: List<MenuItemEntity>,
        val employees: List<EmployeeEntity>,
        val purchaseCategories: List<PurchaseCategoryEntity>,
        val settings: List<AppSettingEntity>
    )

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }
}
