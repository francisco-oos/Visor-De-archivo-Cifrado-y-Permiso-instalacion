package com.frank.visordocumentoscifrado.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.frank.visordocumentoscifrado.activation.ActivationTransportProvider
import com.frank.visordocumentoscifrado.config.AppConfig
import com.frank.visordocumentoscifrado.config.AreaCatalog
import com.frank.visordocumentoscifrado.config.DebugCatalog
import com.frank.visordocumentoscifrado.config.SecurityConfig
import com.frank.visordocumentoscifrado.documents.DocumentRepository
import com.frank.visordocumentoscifrado.license.ActivationStatusClient
import com.frank.visordocumentoscifrado.license.LicenseManager
import com.frank.visordocumentoscifrado.security.DeviceIdentity
import com.frank.visordocumentoscifrado.security.RootDetector
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pantalla principal de la APK.
 *
 * Responsabilidades del AppCore:
 * - notificar instalación por el transporte de activación disponible;
 * - capturar y enviar una solicitud de acceso;
 * - consultar estado mediante ActivationStatusClient;
 * - mostrar documentos permitidos por licencia o, sólo en build debug, habilitar
 *   el bypass de desarrollo.
 *
 * La UI no conoce Telegram ni conocerá la futura API productiva.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SecurityConfig.BLOCK_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        notifyInstallOnce()
        refreshActivationOnOpen()
        showHome()
    }

    private fun baseLayout(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
            setBackgroundColor(0xFF071A2F.toInt())
        }

    private fun title(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 20)
        }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFFE8EEF7.toInt())
            setPadding(0, 4, 0, 4)
        }

    private fun statusLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xDD123A63.toInt())
            setPadding(18, 16, 18, 16)
        }

    private fun input(
        hint: String,
        maxLength: Int,
        type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    ): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = type
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9FB3C8.toInt())
            setSingleLine(false)
        }

    private fun button(text: String): Button =
        Button(this).apply {
            this.text = text
            setTextColor(0xFF071A2F.toInt())
        }

    private fun spinner(options: List<String>): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
            setPadding(0, 8, 0, 8)
        }

    private fun selectedCatalogValue(spinner: Spinner, otherInput: EditText): String {
        val value = spinner.selectedItem?.toString().orEmpty()
        val isOther = value.equals("Otro", ignoreCase = true) ||
            value.equals(AreaCatalog.OTRO, ignoreCase = true)
        return if (isOther) otherInput.text.toString().trim() else value
    }

    private fun showHome() {
        container = baseLayout()
        setContentView(container)

        container.addView(title(AppConfig.APP_DISPLAY_NAME))
        container.addView(label("Versión ${AppConfig.VERSION_NAME} · Vigencia app: ${AppConfig.APP_EXPIRES_AT}"))

        if (LocalDate.now().isAfter(LocalDate.parse(AppConfig.APP_EXPIRES_AT))) {
            container.addView(statusLabel("Esta versión de la aplicación caducó. Solicita una versión actualizada."))
            return
        }

        if (RootDetector.isSuspicious()) {
            container.addView(label("⚠ Equipo con señales de root/emulador."))
            if (SecurityConfig.STRICT_ROOT_BLOCK) {
                container.addView(statusLabel("Acceso bloqueado por política de seguridad."))
                return
            }
        }

        if (AppConfig.DEBUG_MODE) {
            container.addView(statusLabel("DEBUG activo: acceso temporal sin licencia."))
            container.addView(button("Ver información DEBUG").apply { setOnClickListener { showDebugInfo() } })
            showDocuments()
            return
        }

        val lic = LicenseManager.current(this)
        if (lic == null) showActivation() else showDocuments()
    }

    private fun showDebugInfo() {
        container = baseLayout()
        setContentView(container)
        container.addView(title("DEBUG"))
        container.addView(label("Documentos en index.json: ${DocumentRepository.list(this).size}"))
        container.addView(label("Documentos visibles: ${DocumentRepository.listAllowed(this).size}"))
        container.addView(label("Device hash: ${DeviceIdentity.deviceHash(this).take(18)}..."))
        container.addView(label("Install ID: ${DeviceIdentity.installId(this)}"))
        container.addView(label("Transporte activación: ${ActivationTransportProvider.current().name}"))
        container.addView(button("Regresar").apply { setOnClickListener { showHome() } })
    }

    private fun showActivation() {
        val prefs = getSharedPreferences("request_state", MODE_PRIVATE)
        val lastRequestId = prefs.getString("last_request_id", null)
        val lastStatus = prefs.getString("last_status", "SIN_SOLICITUD") ?: "SIN_SOLICITUD"

        if (lastRequestId != null) {
            showWaitingState(lastRequestId, lastStatus)
            return
        }

        container.addView(statusLabel("Equipo pendiente de autorización"))
        if (!ActivationTransportProvider.current().isConfigured()) {
            container.addView(
                label(
                    "El canal productivo de activación todavía no está configurado. " +
                        "Esta rama puede probarse como build debug; la API se conectará después al mismo contrato."
                )
            )
        } else {
            container.addView(
                label(
                    "Captura tus datos y presiona Solicitar acceso. La aplicación consultará automáticamente " +
                        "el estado cuando la abras."
                )
            )
        }

        val name = input("Nombre completo", 80)
        val id = input("ID empleado", 20, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val puestoSpinner = spinner(DebugCatalog.POSITIONS)
        val puestoOtro = input("Especifica puesto si elegiste Otro", 50)
        val areaSpinner = spinner(DebugCatalog.AREAS)
        val areaOtro = input("Especifica área si elegiste Otro", 50)
        val phone = input("Teléfono", 15, InputType.TYPE_CLASS_PHONE)
        val project = input("Proyecto", 40)
        val obs = input("Observaciones", 160)

        puestoOtro.visibility = View.GONE
        areaOtro.visibility = View.GONE

        puestoSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, rowId: Long) {
                puestoOtro.visibility = if (DebugCatalog.POSITIONS[position] == "Otro") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        areaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, rowId: Long) {
                areaOtro.visibility = if (DebugCatalog.AREAS[position] == AreaCatalog.OTRO) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        project.setText(AppConfig.PROJECT_NAME)

        container.addView(name)
        container.addView(id)
        container.addView(label("Puesto / categoría"))
        container.addView(puestoSpinner)
        container.addView(puestoOtro)
        container.addView(label("Área / departamento"))
        container.addView(areaSpinner)
        container.addView(areaOtro)
        container.addView(phone)
        container.addView(project)
        container.addView(obs)

        container.addView(button("Solicitar acceso").apply {
            setOnClickListener {
                val validation = validateRequestFields(
                    name.text.toString(),
                    id.text.toString(),
                    selectedCatalogValue(puestoSpinner, puestoOtro),
                    selectedCatalogValue(areaSpinner, areaOtro),
                    phone.text.toString()
                )
                if (validation != null) {
                    Toast.makeText(this@MainActivity, validation, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val request = buildEventJson(
                    name = name.text.toString().trim(),
                    id = id.text.toString().trim(),
                    puesto = selectedCatalogValue(puestoSpinner, puestoOtro),
                    area = selectedCatalogValue(areaSpinner, areaOtro),
                    phone = phone.text.toString().trim(),
                    project = project.text.toString().trim(),
                    obs = obs.text.toString().trim(),
                    eventType = "ACCESS_REQUEST"
                )
                sendAccessRequest(request)
            }
        })
    }

    private fun validateRequestFields(name: String, id: String, position: String, area: String, phone: String): String? {
        if (name.trim().length < 3) return "Escribe el nombre completo."
        if (id.trim().length < 2) return "Escribe un ID de empleado válido."
        if (position.trim().length < 3) return "Selecciona o escribe el puesto."
        if (area.trim().length < 2) return "Selecciona o escribe el área."
        val digits = phone.filter { it.isDigit() }
        if (phone.isNotBlank() && digits.length !in 10..15) return "El teléfono debe tener entre 10 y 15 dígitos."
        return null
    }

    private fun showWaitingState(requestId: String, status: String) {
        val displayStatus = when (status.uppercase()) {
            "APPROVED" -> "Aprobado"
            "REJECTED" -> "Rechazado"
            "EXPIRED" -> "Vencido"
            "PENDING" -> "En revisión"
            else -> "En revisión"
        }

        container.addView(statusLabel("Solicitud enviada · Estado: $displayStatus"))
        container.addView(label("La aplicación consultará automáticamente el estado al abrirse. También puedes consultarlo manualmente."))
        container.addView(label("Folio: $requestId"))

        container.addView(button("Consultar estado").apply { setOnClickListener { checkActivationStatus(showToast = true) } })
        container.addView(button("Actualizar pantalla").apply { setOnClickListener { showHome() } })
    }

    private fun refreshActivationOnOpen() {
        if (AppConfig.DEBUG_MODE || LicenseManager.current(this) != null) return
        val hasRequest = getSharedPreferences("request_state", MODE_PRIVATE).getString("last_request_id", null) != null
        if (!hasRequest) return
        checkActivationStatus(showToast = false)
    }

    private fun checkActivationStatus(showToast: Boolean) {
        ActivationStatusClient.check(this) { result ->
            getSharedPreferences("request_state", MODE_PRIVATE)
                .edit()
                .putString("last_status", result.status)
                .apply()
            if (showToast || result.status.uppercase() != "PENDING") {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
            showHome()
        }
    }

    private fun notifyInstallOnce() {
        val transport = ActivationTransportProvider.current()
        if (!transport.isConfigured()) return

        val prefs = getSharedPreferences("request_state", MODE_PRIVATE)
        if (prefs.getBoolean("install_notice_sent", false)) return

        val request = buildEventJson(
            name = "",
            id = "",
            puesto = "",
            area = "",
            phone = "",
            project = AppConfig.PROJECT_NAME,
            obs = "Aviso automático de instalación.",
            eventType = "INSTALL_EVENT"
        )

        transport.sendEvent(this, request) { ok, _ ->
            if (ok) prefs.edit().putBoolean("install_notice_sent", true).apply()
        }
    }

    private fun buildEventJson(
        name: String,
        id: String,
        puesto: String,
        area: String,
        phone: String,
        project: String,
        obs: String,
        eventType: String
    ): JSONObject {
        val info = DeviceIdentity.info(this)
        val requestedAt = LocalDateTime.now().toString()
        val hashShort = info["device_hash"].orEmpty().take(10)
        val installShort = info["install_id"].orEmpty().take(8)
        val safeEmployee = id.ifBlank { "SIN_ID" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val requestId = "${eventType}_${safeEmployee}_${hashShort}_${installShort}"

        return JSONObject().apply {
            put("event_type", eventType)
            put("schema", "VISOR_APP_EVENT_V1")
            put("request_id", requestId)
            put("requested_at", requestedAt)
            put("app_name", AppConfig.APP_DISPLAY_NAME)
            put("app_version", AppConfig.VERSION_NAME)
            put("app_expires_at", AppConfig.APP_EXPIRES_AT)
            put("company", AppConfig.COMPANY_NAME)
            put("employee_name", name)
            put("employee_id", id)
            put("position", puesto)
            put("area", AreaCatalog.normalize(area))
            put("phone", phone)
            put("project", project)
            put("observations", obs)
            put("manufacturer", info["manufacturer"])
            put("brand", info["brand"])
            put("model", info["model"])
            put("android", info["android"])
            put("sdk", info["sdk"])
            put("install_id", info["install_id"])
            put("device_hash", info["device_hash"])
        }
    }

    private fun sendAccessRequest(request: JSONObject) {
        val transport = ActivationTransportProvider.current()
        transport.sendEvent(this, request) { ok, msg ->
            if (ok) {
                getSharedPreferences("request_state", MODE_PRIVATE)
                    .edit()
                    .putString("last_request_id", request.optString("request_id"))
                    .putString("last_status", "PENDING")
                    .apply()
                Toast.makeText(this, "Solicitud enviada. Estado: en revisión.", Toast.LENGTH_LONG).show()
                showHome()
            } else {
                Toast.makeText(this, "No se pudo enviar la solicitud: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDocuments() {
        val lic = LicenseManager.current(this)

        if (AppConfig.DEBUG_MODE) {
            container.addView(statusLabel("Modo DEBUG: todos los documentos visibles."))
        } else if (lic != null) {
            val accessText = if (LicenseManager.hasAllAccess(lic)) {
                "Todos los departamentos"
            } else {
                lic.allowedAreas
                    .map { AreaCatalog.normalize(it) }
                    .filter { AreaCatalog.AREA_IDS.contains(it) }
                    .joinToString(", ") { AreaCatalog.displayName(it) }
                    .ifBlank { "Sin áreas asignadas" }
            }
            container.addView(statusLabel("Autorizado: ${lic.employeeName}"))
            container.addView(label("Acceso: $accessText · vence: ${lic.expiresAt}"))
        }

        val search = input("Buscar manual, área o palabra clave", 80)
        container.addView(search)

        val listScroll = ScrollView(this).apply { setFillViewport(false) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(list)

        val expandedAreas = mutableSetOf<String>()

        fun areaHeader(areaId: String, count: Int, expanded: Boolean): TextView =
            TextView(this).apply {
                text = if (expanded) "▾ ${AreaCatalog.displayName(areaId)} · $count documento(s)" else "▸ ${AreaCatalog.displayName(areaId)} · $count documento(s)"
                textSize = 17f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xDD123A63.toInt())
                setPadding(18, 16, 18, 16)
            }

        fun docButton(docTitle: String): Button =
            Button(this).apply {
                text = "📄  $docTitle"
                setTextColor(0xFF0B2545.toInt())
                setPadding(8, 8, 8, 8)
            }

        fun refresh(q: String = "") {
            list.removeAllViews()
            val docs = DocumentRepository.search(this, q)
            val grouped = docs.groupBy { AreaCatalog.normalize(it.areaId) }.toSortedMap()

            if (DocumentRepository.list(this).isEmpty()) {
                list.addView(label("No hay manuales cifrados. Usa tools/ABRIR_ENCRIPTADOR.bat, cifra y recompila la APK."))
                return
            }

            if (docs.isEmpty()) {
                list.addView(label("No hay documentos permitidos para esta licencia."))
                return
            }

            grouped.forEach { (area, areaDocs) ->
                val expanded = expandedAreas.contains(area)
                val header = areaHeader(area, areaDocs.size, expanded)
                header.setOnClickListener {
                    if (expandedAreas.contains(area)) expandedAreas.remove(area) else expandedAreas.add(area)
                    refresh(search.text.toString())
                }
                list.addView(header)

                if (expanded) {
                    list.addView(label("Departamento: ${AreaCatalog.displayName(area)}"))
                    areaDocs.sortedBy { it.title }.forEach { doc ->
                        list.addView(docButton(doc.title).apply {
                            setOnClickListener {
                                PdfActivity.selectedFile = doc.file
                                PdfActivity.selectedTitle = doc.title
                                startActivity(Intent(this@MainActivity, PdfActivity::class.java))
                            }
                        })
                    }
                }
            }
        }

        container.addView(button("Buscar / actualizar lista").apply {
            setOnClickListener {
                expandedAreas.clear()
                DocumentRepository.search(this@MainActivity, search.text.toString())
                    .forEach { expandedAreas.add(AreaCatalog.normalize(it.areaId)) }
                refresh(search.text.toString())
            }
        })

        container.addView(listScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }
}
