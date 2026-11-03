package com.abugyda.dbd.webview

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

class WebviewBridge {

  data class GlobalSettings(
    val lineStyle: String, val showGrid: Boolean, val gridSize: Int,
    val snapToGrid: Boolean = false, val snapAlignment: String = "grid",
    val overrideDiagramAlignment: Boolean = false,
    val showTableNotes: Boolean = true, val showFieldNotes: Boolean = true,
    val tableNoteMaxLines: Int = 2, val fieldNoteMaxLines: Int = 2,
    val fontSize: Int = 12,
    val fontSizes: Map<String, Int> = emptyMap(),
    val tableDensity: String = "compact",
    val groupMergeTolerance: Int = 32,
  )

  data class SchemaPayload(
    val tables: List<DbTable>, val relationships: List<DbRelationship>,
    val projectSettings: DbProject, val notes: List<DbNote>,
    val groups: List<Map<String, Any?>> = emptyList(),
    val viewport: Map<String, Double>? = null,
    val displaySettings: Map<String, Any?> = emptyMap(),
    val sourceCount: Int = 0
  )

  data class DbProject(
    val zoom: Double? = null, val panX: Double? = null, val panY: Double? = null,
    val databaseType: String? = null, val note: String? = null
  )

  data class DbTable(
    val id: String, val schema: String, val name: String,
    val alias: String? = null, val fields: List<DbField>,
    val settings: Map<String, String>? = null, val indexes: List<DbIndex>? = null,
    val note: String? = null, val color: String? = null,
    val horizontal: Int? = null, val vertical: Int? = null, val width: Int? = null,
    val hiddenRelatedTables: List<String> = emptyList(),
    val checks: List<DbCheck> = emptyList(),
  )

  data class DbField(
    val name: String, val isPrimaryKey: Boolean = false, val isForeignKey: Boolean = false,
    val isUnique: Boolean, val isNotNull: Boolean, val type: String,
    val isIndexed: Boolean = false,
    val default: String? = null, val enumValues: List<String>? = null,
    val reference: DbReference? = null, val note: String? = null, val typeCategory: String? = null,
    val isArray: Boolean = false, val checks: List<String> = emptyList(),
    val identity: String? = null, val generated: String? = null,
  )

  data class DbCheck(val name: String? = null, val expression: String, val columns: List<String>)

  data class DbReference(
    val symbol: String,
    val toSchema: String,
    val toTable: String,
    val toColumn: String
  )

  data class WayPoint(val x: Double, val y: Double, val from: String)

  data class DbRelationship(
    val fromSchema: String, val fromTable: String, val fromColumns: List<String>,
    val toSchema: String, val toTable: String, val toColumns: List<String>,
    val type: String, val settings: Map<String, String>? = null,
    val sourceAnchor: String? = null, val targetAnchor: String? = null,
    val waypoints: List<WayPoint>? = null
  )

  data class DbNote(
    val id: String, val name: String, val content: String,
    val horizontal: Int, val vertical: Int, val width: Int,
    val height: Int? = null, val color: String? = null,
    val target: String? = null, val targetAnchor: String? = null,
  )

  data class DbIndex(
    val columns: List<String>,
    val settings: Map<String, String>? = null,
    val raw: String? = null
  )

  data class TextStyle(
    val foreground: String? = null, val background: String? = null,
    val bold: Boolean = false, val italic: Boolean = false
  )

  // ── IDE → Webview ─────────────────────────────────────────────────────────

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes(
    JsonSubTypes.Type(value = Server.UpdateSchemaPayload::class, name = "UPDATE_SCHEMA_PAYLOAD"),
    JsonSubTypes.Type(value = Server.UpdateTheme::class, name = "UPDATE_THEME"),
    JsonSubTypes.Type(value = Server.SchemaError::class, name = "SCHEMA_ERROR"),
    JsonSubTypes.Type(value = Server.UpdateGlobalSettings::class, name = "UPDATE_GLOBAL_SETTINGS")
  )
  sealed class Server {
    data class SchemaError(val message: String) : Server()
    data class UpdateSchemaPayload(
      val payload: SchemaPayload,
      val settings: GlobalSettings? = null
    ) : Server()

    data class UpdateTheme(
      val theme: String, val colors: Map<String, String> = emptyMap(),
      val textStyles: Map<String, TextStyle> = emptyMap()
    ) : Server()

    data class UpdateGlobalSettings(val settings: GlobalSettings) : Server()
  }

  // ── Webview → IDE ─────────────────────────────────────────────────────────

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes(
    JsonSubTypes.Type(value = Client.Log::class, name = "LOG"),
    JsonSubTypes.Type(value = Client.Ready::class, name = "READY"),
    JsonSubTypes.Type(value = Client.UpdateCursor::class, name = "UPDATE_CURSOR"),
    JsonSubTypes.Type(value = Client.RefreshSchema::class, name = "REFRESH_SCHEMA"),
    JsonSubTypes.Type(value = Client.ConfigureDiagram::class, name = "CONFIGURE_DIAGRAM"),
    JsonSubTypes.Type(value = Client.SavePresentation::class, name = "SAVE_PRESENTATION"),
    JsonSubTypes.Type(value = Client.UpdateTablePos::class, name = "UPDATE_TABLE_POS"),
    JsonSubTypes.Type(value = Client.UpdateNotePos::class, name = "UPDATE_NOTE_POS"),
    JsonSubTypes.Type(value = Client.UpdateTableColor::class, name = "UPDATE_TABLE_COLOR"),
    JsonSubTypes.Type(value = Client.UpdateNoteColor::class, name = "UPDATE_NOTE_COLOR"),
  )
  sealed class Client {
    data class Log(val level: String, val message: String) : Client()
    data object Ready : Client()
    data class UpdateCursor(val cursor: String? = null) : Client()
    data object RefreshSchema : Client()
    data class ConfigureDiagram(val includeTableIds: List<String> = emptyList()) : Client()
    data class SavePresentation(
      val tables: List<Map<String, Any?>> = emptyList(),
      val notes: List<Map<String, Any?>> = emptyList(),
      val deletedNoteIds: List<String> = emptyList(),
      val groups: List<Map<String, Any?>> = emptyList(),
      val viewport: Map<String, Double>? = null,
      val displaySettings: Map<String, Any?> = emptyMap()
    ) : Client()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UpdateTablePos(
      val tableName: String,
      val x: Int,
      val y: Int,
      val width: Int? = null
    ) : Client()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UpdateNotePos(
      val name: String,
      val x: Int,
      val y: Int,
      val width: Int,
      val height: Int
    ) : Client()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UpdateTableColor(val tableName: String, val color: String) : Client()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UpdateNoteColor(val noteId: String, val color: String) : Client()
  }
}
