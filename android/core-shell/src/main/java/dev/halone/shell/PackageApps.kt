package dev.halone.shell

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** What a Halone app may ask the bridge for beyond what every app gets. */
enum class Permission(val wire: String, val prompt: String) {
    NETWORK_TCP("network.tcp", "Connect to any device or server"),
    NETWORK_UDP("network.udp", "Send and receive network packets"),
    CLIPBOARD("clipboard", "Read what you copy"),
    FILES("files", "Read and change your files"),
    SENSORS("sensors", "Know where you are looking"),
    WINDOWS("windows", "See and switch your open windows"),
    APPS("apps", "See and open your apps"),
    SCREEN("screen", "Capture what you see");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire }
    }
}

/** A package's `halone.json`, checked. */
data class PackageManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val icon: String?,
    val entry: String,
    val widthPx: Int,
    val heightPx: Int,
    val permissions: List<Permission>,
    val opens: List<String>,
    /** The tile's colour in the launcher, as `#rrggbb`. */
    val color: String? = null,
    /** The oldest Halone the app runs on. */
    val minHalone: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("version", version)
        .put("description", description)
        .put("icon", icon ?: JSONObject.NULL)
        .put("entry", entry)
        .put("window", JSONObject().put("width", widthPx).put("height", heightPx))
        .put("permissions", JSONArray(permissions.map { it.wire }))
        .put("opens", JSONArray(opens))
        .put("color", color ?: JSONObject.NULL)
        .put("minHalone", minHalone ?: JSONObject.NULL)

    companion object {
        const val FILE = "halone.json"
        private val COLOR = Regex("^#[0-9a-fA-F]{6}$")
        private val ID = Regex("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$")
        private val VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")
        private const val MAX_ID = 63
        private const val MIN_WINDOW_PX = 200
        private const val MAX_WINDOW_PX = 4096

        /**
         * Throws `invalid-package` naming what is wrong; [reserved] are ids the shell's own apps hold.
         * Given the running [halone] version, throws `incompatible` for an app that needs a newer one.
         */
        fun parse(text: String, reserved: Set<String> = emptySet(), halone: String? = null): PackageManifest {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: invalid("$FILE is not a JSON object")
            fun string(key: String) = (json.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val id = string("id") ?: invalid("id is missing")
            if (!ID.matches(id) || id.length > MAX_ID) invalid("id must be reverse-DNS in lowercase, such as dev.example.app: $id")
            if (id in reserved || id.startsWith(WebApp.ID_PREFIX)) invalid("id is reserved: $id")
            val version = string("version") ?: invalid("version is missing")
            if (!VERSION.matches(version)) invalid("version must be major.minor.patch: $version")
            val entry = string("entry") ?: "index.html"
            val window = json.optJSONObject("window")
            fun size(key: String, default: Int): Int {
                val value = window?.opt(key) ?: return default
                return (value as? Number)?.toInt()?.takeIf { it in MIN_WINDOW_PX..MAX_WINDOW_PX }
                    ?: invalid("window.$key must be between $MIN_WINDOW_PX and $MAX_WINDOW_PX")
            }
            val permissions = strings(json, "permissions").map { Permission.of(it) ?: invalid("unknown permission: $it") }.distinct()
            val name = (string("name") ?: invalid("name is missing")).take(MAX_NAME)
            val minHalone = string("minHalone")
            if (minHalone != null) {
                if (!VERSION.matches(minHalone)) invalid("minHalone must be major.minor.patch: $minHalone")
                if (halone != null && HalonePackage.compareVersions(minHalone, halone) > 0) {
                    throw HaloneException("incompatible", "$name needs Halone $minHalone or newer; this is ${halone.substringBefore('-')}")
                }
            }
            return PackageManifest(
                id = id,
                name = name,
                version = version,
                description = string("description").orEmpty().take(MAX_DESCRIPTION),
                icon = string("icon")?.let { safePath(it) },
                entry = safePath(entry),
                widthPx = size("width", DEFAULT_WIDTH_PX),
                heightPx = size("height", DEFAULT_HEIGHT_PX),
                permissions = permissions,
                opens = strings(json, "opens"),
                color = string("color")?.also { if (!COLOR.matches(it)) invalid("color must be #rrggbb: $it") }?.lowercase(),
                minHalone = minHalone,
            )
        }

        private fun strings(json: JSONObject, key: String): List<String> {
            val value = json.opt(key) ?: return emptyList()
            val array = value as? JSONArray ?: invalid("$key must be a list")
            return (0 until array.length()).map { array.opt(it) as? String ?: invalid("$key must hold strings") }
        }

        /** A path inside the package, `/`-separated and relative, that cannot leave it. */
        fun safePath(path: String): String {
            val parts = path.split('/')
            if (path.startsWith("/") || '\\' in path || parts.any { it.isEmpty() || it == "." || it == ".." }) {
                invalid("not a path inside the package: $path")
            }
            return path
        }

        private fun invalid(message: String): Nothing = throw HaloneException("invalid-package", message)

        private const val MAX_NAME = 80
        private const val MAX_DESCRIPTION = 300
        const val DEFAULT_WIDTH_PX = 1024
        const val DEFAULT_HEIGHT_PX = 768
    }
}

/** A `.halone` file, read and checked but not yet installed. */
class HalonePackage private constructor(val file: File, val manifest: PackageManifest, val iconPng: ByteArray?) {
    companion object {
        const val EXTENSION = "halone"
        const val MIME_TYPE = "application/x-halone"
        private const val MAX_ENTRIES = 10_000
        private const val MAX_BYTES = 256L * 1024 * 1024
        private const val MAX_ICON_BYTES = 1024 * 1024
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)

        fun read(file: File, reserved: Set<String> = emptySet(), halone: String? = null): HalonePackage {
            val zip = runCatching { ZipFile(file) }.getOrElse { throw HaloneException("invalid-package", "not a .halone package") }
            zip.use {
                val entries = zip.entries().toList()
                if (entries.size > MAX_ENTRIES) throw HaloneException("invalid-package", "more than $MAX_ENTRIES files")
                var total = 0L
                entries.forEach { entry ->
                    if (!entry.isDirectory) PackageManifest.safePath(entry.name)
                    total += entry.size.coerceAtLeast(0)
                }
                if (total > MAX_BYTES) throw HaloneException("invalid-package", "larger than ${MAX_BYTES / 1024 / 1024} MB unpacked")
                val manifestEntry = zip.getEntry(PackageManifest.FILE)
                    ?: throw HaloneException("invalid-package", "${PackageManifest.FILE} is missing")
                val manifest = PackageManifest.parse(zip.getInputStream(manifestEntry).bufferedReader().readText(), reserved, halone)
                if (zip.getEntry(manifest.entry)?.isDirectory != false) {
                    throw HaloneException("invalid-package", "entry ${manifest.entry} is not in the package")
                }
                val icon = manifest.icon?.let { path ->
                    val entry = zip.getEntry(path) ?: throw HaloneException("invalid-package", "icon $path is not in the package")
                    if (entry.size > MAX_ICON_BYTES) throw HaloneException("invalid-package", "icon $path is over 1 MB")
                    zip.getInputStream(entry).use { it.readBytes() }.also { bytes ->
                        if (bytes.size < PNG.size || !bytes.copyOf(PNG.size).contentEquals(PNG)) {
                            throw HaloneException("invalid-package", "icon $path is not a PNG")
                        }
                    }
                }
                return HalonePackage(file, manifest, icon)
            }
        }

        /** The larger of two `major.minor.patch` versions compares above; a `-alpha` or other suffix is ignored. */
        fun compareVersions(a: String, b: String): Int {
            val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            return left.zip(right).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
        }
    }
}

/** A Halone app installed from a package, and what the wearer granted it. */
data class InstalledApp(
    val manifest: PackageManifest,
    val granted: Set<Permission>,
    /** Where the package came from, as the install prompt showed it. */
    val source: String,
    val installedAt: Long,
    val updatedAt: Long,
    /** Loaded from a developer's server instead of installed files, while developer mode is on. */
    val devUrl: String? = null,
) {
    val id: String get() = manifest.id

    /** The app's own origin: storage and cookies are its alone, and it can never pass for a system app. */
    val origin: String get() = "https://$id.$ORIGIN_SUFFIX"

    val entryUrl: String get() = devUrl ?: "$origin/${manifest.entry}"

    fun toAppInfo(iconUrl: String?) = AppInfo(
        id = id,
        name = manifest.name,
        description = manifest.description,
        icon = "",
        entry = entryUrl,
        widthPx = manifest.widthPx,
        heightPx = manifest.heightPx,
        opens = manifest.opens,
        kind = AppKind.PACKAGE,
        iconUrl = iconUrl,
        permissions = granted.map { it.wire },
        dev = devUrl != null,
        color = manifest.color,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("manifest", manifest.toJson())
        .put("granted", JSONArray(granted.map { it.wire }))
        .put("source", source)
        .put("installedAt", installedAt)
        .put("updatedAt", updatedAt)
        .put("devUrl", devUrl ?: JSONObject.NULL)

    companion object {
        const val ORIGIN_SUFFIX = "apps.halone.local"

        fun fromJson(json: JSONObject): InstalledApp {
            val manifest = PackageManifest.parse(json.getJSONObject("manifest").toString())
            val granted = json.optJSONArray("granted")?.let { array ->
                (0 until array.length()).mapNotNull { Permission.of(array.optString(it)) }
            }.orEmpty().toSet()
            return InstalledApp(
                manifest,
                granted intersect manifest.permissions.toSet(),
                json.optString("source"),
                json.optLong("installedAt"),
                json.optLong("updatedAt"),
                json.optString("devUrl").takeIf { it.isNotEmpty() && !json.isNull("devUrl") },
            )
        }
    }
}

/**
 * Halone apps installed from packages: each unpacked under [root] in a folder
 * per version, swapped in whole on an update, with the registry in [store].
 * Call on one thread; unpacking is plain file work, quick for app-sized packages.
 */
class PackageApps(
    private val root: File,
    private val store: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val apps = ArrayList<InstalledApp>()

    var onChange: (() -> Unit)? = null

    init {
        store.load()?.let { text ->
            runCatching {
                val array = JSONArray(text)
                for (i in 0 until array.length()) runCatching { apps += InstalledApp.fromJson(array.getJSONObject(i)) }
            }
        }
    }

    fun list(): List<InstalledApp> = apps.toList()

    fun find(id: String?): InstalledApp? = id?.let { apps.firstOrNull { app -> app.id == it } }

    /** Where an installed app's files are; null for a dev app, which has none. */
    fun filesOf(id: String): File? = find(id)?.takeIf { it.devUrl == null }?.let { File(root, "${it.id}/${it.manifest.version}") }

    fun iconUrl(app: InstalledApp): String? {
        val icon = app.manifest.icon ?: return null
        return if (app.devUrl != null) app.devUrl.substringBeforeLast('/') + "/" + icon else "${app.origin}/$icon"
    }

    /** What installing [pkg] would add: its permissions not already granted to the version installed. */
    fun newPermissions(pkg: HalonePackage): List<Permission> {
        val granted = find(pkg.manifest.id)?.granted.orEmpty()
        return pkg.manifest.permissions.filter { it !in granted }
    }

    /**
     * Installs [pkg], granting what its manifest asks for: the wearer agreed in
     * the prompt. An update needs a higher version; the old files go once the
     * new ones are in place.
     */
    fun install(pkg: HalonePackage, source: String): InstalledApp {
        val manifest = pkg.manifest
        val previous = find(manifest.id)
        if (previous != null && previous.devUrl == null && HalonePackage.compareVersions(manifest.version, previous.manifest.version) <= 0) {
            throw HaloneException("not-newer", "${manifest.name} ${previous.manifest.version} is installed; this package is ${manifest.version}")
        }
        val folder = File(root, manifest.id)
        val target = File(folder, manifest.version)
        val unpacking = File(folder, "${manifest.version}.unpacking")
        unpacking.deleteRecursively()
        unpack(pkg.file, unpacking)
        target.deleteRecursively()
        if (!unpacking.renameTo(target)) {
            unpacking.deleteRecursively()
            throw HaloneException("io", "could not install ${manifest.name}")
        }
        folder.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
        val now = clock()
        val app = InstalledApp(manifest, manifest.permissions.toSet(), source, previous?.installedAt ?: now, now)
        put(app)
        return app
    }

    /** A developer's app, loaded from [url] and granted what its manifest asks for, replacing any installed copy. */
    fun installDev(manifest: PackageManifest, url: String): InstalledApp {
        File(root, manifest.id).deleteRecursively()
        val now = clock()
        val app = InstalledApp(manifest, manifest.permissions.toSet(), url, find(manifest.id)?.installedAt ?: now, now, devUrl = url)
        put(app)
        return app
    }

    fun remove(id: String) {
        if (!apps.removeAll { it.id == id }) return
        File(root, id).deleteRecursively()
        changed()
    }

    /** Drops every dev app, as leaving developer mode does. */
    fun removeDevApps() {
        if (apps.removeAll { it.devUrl != null }) changed()
    }

    private fun put(app: InstalledApp) {
        val index = apps.indexOfFirst { it.id == app.id }
        if (index >= 0) apps[index] = app else apps += app
        changed()
    }

    private fun unpack(file: File, into: File) {
        ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                val out = File(into, PackageManifest.safePath(entry.name))
                if (!out.canonicalPath.startsWith(into.canonicalPath + File.separator)) {
                    throw HaloneException("invalid-package", "not a path inside the package: ${entry.name}")
                }
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun changed() {
        store.save(JSONArray(apps.map { it.toJson() }).toString())
        onChange?.invoke()
    }
}
