package com.abugyda.dbd.builders

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.*
import com.abugyda.dbd.webview.WebviewBridge.*
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence

object ErdDataBuilder {

  private val KNOWN_BUILTIN_TYPES = setOf(
    "int", "int2", "int4", "int8", "integer", "bigint", "smallint",
    "serial", "bigserial", "smallserial",
    "float", "float4", "float8", "real", "double", "double precision",
    "numeric", "decimal", "money",
    "bool", "boolean",
    "char", "bpchar", "character", "character varying",
    "varchar", "text", "name", "bytea", "uuid", "citext",
    "date", "time", "timetz", "timestamp", "timestamptz", "interval",
    "time without time zone", "time with time zone",
    "timestamp without time zone", "timestamp with time zone",
    "json", "jsonb", "xml", "hstore",
    "point", "line", "lseg", "box", "path", "polygon", "circle",
    "cidr", "inet", "macaddr", "macaddr8",
    "bit", "varbit", "tsvector", "tsquery",
    "oid", "void", "record", "any", "anyelement", "anyarray",
    "pg_lsn", "txid_snapshot", "xid", "cid", "tid", "regclass", "regtype",
  )

  private val DEFAULT_REGEX =
    Regex("""DEFAULT\s+((?:'[^']*'|"[^"]*"|\w+\([^)]*\)|\S+))""", RegexOption.IGNORE_CASE)
  private val INLINE_ENUM_REGEX = Regex("""ENUM\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)

  private const val TYPE_NAME = """((?:"(?:""|[^"])+"|\w+)(?:\s*\.\s*(?:"(?:""|[^"])+"|\w+))?)"""
  private val CREATE_ENUM_REGEX = Regex(
    """CREATE\s+TYPE\s+$TYPE_NAME\s+AS\s+ENUM\s*\(((?:'(?:''|[^'])*'|[^')])*)\)""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
  )
  private val CREATE_COMPOSITE_REGEX =
    Regex("""CREATE\s+TYPE\s+$TYPE_NAME\s+AS\s*\(""", setOf(RegexOption.IGNORE_CASE))
  private val CREATE_RANGE_REGEX = Regex(
    """CREATE\s+TYPE\s+$TYPE_NAME\s+AS\s+RANGE\s*\(""",
    setOf(RegexOption.IGNORE_CASE)
  )
  private val CREATE_BASE_REGEX = Regex(
    """CREATE\s+TYPE\s+$TYPE_NAME\s*\(\s*INPUT\s*=""",
    setOf(RegexOption.IGNORE_CASE)
  )
  private val CREATE_DOMAIN_REGEX =
    Regex("""CREATE\s+DOMAIN\s+$TYPE_NAME\s+AS\s+\w""", setOf(RegexOption.IGNORE_CASE))

  fun build(
    yamlFile: YAMLFile,
    project: Project,
    sourcesOverride: List<SqlSourceLoader.Source>? = null
  ): SchemaPayload {
    check(
      PsiTreeUtil.findChildOfType(
        yamlFile,
        com.intellij.psi.PsiErrorElement::class.java
      ) == null
    ) {
      "Invalid diagram YAML in ${yamlFile.name}; correct its syntax before refreshing"
    }
    val root = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping
      ?: error("Invalid diagram YAML in ${yamlFile.name}: expected a root mapping")
    check(root.getKeyValueByKey("schema")?.value is YAMLMapping) {
      "Invalid diagram YAML in ${yamlFile.name}: schema must be a mapping"
    }
    check((root.getKeyValueByKey("schema")?.value as YAMLMapping).getKeyValueByKey("imports")?.value is YAMLSequence) {
      "Invalid diagram YAML in ${yamlFile.name}: schema.imports must be a list"
    }

    val sources = sourcesOverride ?: SqlSourceLoader.load(yamlFile, project)
    val dbTables = mutableListOf<DbTable>()
    val rels = mutableListOf<DbRelationship>()
    fun qualified(raw: String, schema: String): Pair<String, String> {
      val names = raw.split('.').map { it.trim().removeSurrounding("\"") }
      return (if (names.size > 1) names[names.size - 2] else schema) to names.last()
    }

    fun tableId(raw: String, schema: String): String =
      qualified(raw, schema).let { "${it.first}.${it.second}" }

    fun owner(element: PsiElement, schema: String): String? {
      for (parent in generateSequence(element.parent) { it.parent }) {
        if (parent is SqlTableDefinition) return tableId(
          (parent.nameElement?.text ?: parent.name),
          schema
        )
        if (parent is SqlAlterStatement) return parent.alterTargetReference?.text?.let {
          tableId(
            it,
            schema
          )
        }
      }
      return null
    }

    val keys = mutableMapOf<String, MutableList<Pair<Boolean, List<String>>>>()
    val indexes = mutableMapOf<String, MutableList<DbIndex>>()
    val enums = mutableMapOf<String, List<String>>()
    val customTypes = mutableMapOf<String, String>()
    val checks = mutableMapOf<String, MutableList<DbCheck>>()
    for ((psi, schema) in sources) {
      for ((pattern, category) in listOf(
        CREATE_COMPOSITE_REGEX to "RECORD",
        CREATE_RANGE_REGEX to "RANGE",
        CREATE_DOMAIN_REGEX to "DOMAIN",
        CREATE_BASE_REGEX to "BASE"
      )) {
        pattern.findAll(psi.text)
          .forEach { customTypes[tableId(it.groupValues[1], schema)] = category }
      }
      CREATE_ENUM_REGEX.findAll(psi.text).forEach { m ->
        enums[tableId(m.groupValues[1], schema)] =
          Regex("""'((?:''|[^'])*)'""").findAll(m.groupValues[2])
            .map { it.groupValues[1].replace("''", "'") }.toList()
      }
      for (key in PsiTreeUtil.findChildrenOfType(psi, SqlTableKeyDefinition::class.java)) {
        val id = owner(key, schema) ?: continue
        val columns = key.columnsRef.names().toList()
        keys.getOrPut(id) { mutableListOf() }.add(key.isPrimary to columns)
        indexes.getOrPut(id) { mutableListOf() }
          .add(DbIndex(columns, mapOf("name" to (key.name ?: "")), key.text))
      }
      for (index in PsiTreeUtil.findChildrenOfType(psi, SqlCreateIndexStatement::class.java)) {
        val id = index.targetReference?.text?.let { tableId(it, schema) } ?: continue
        val columns = index.columnReferences.orEmpty().mapNotNull { it.name }
        indexes.getOrPut(id) { mutableListOf() }
          .add(DbIndex(columns, mapOf("name" to (index.name ?: "")), index.text))
      }
      for (constraint in PsiTreeUtil.findChildrenOfType(psi, SqlConstraintDefinition::class.java)) {
        if (constraint.constraintType != SqlConstraintDefinition.Type.CHECK) continue
        val id = owner(constraint, schema) ?: continue
        val expression =
          constraint.getConstraintParameter(SqlConstraintDefinition.EXPRESSION) ?: continue
        val references = (listOfNotNull(expression as? SqlReferenceExpression) +
            PsiTreeUtil.findChildrenOfType(expression, SqlReferenceExpression::class.java))
          .filter { it.referenceElementType.targetKind == com.intellij.database.model.ObjectKind.COLUMN }
          .mapNotNull { it.name }.toMutableList()
        val column = PsiTreeUtil.getParentOfType(constraint, SqlColumnDefinition::class.java)
        column?.name?.let { references.add(it) }
        checks.getOrPut(id) { mutableListOf() }
          .add(DbCheck(constraint.name, expression.text, references.distinct()))
      }
    }
    for ((psi, schema) in sources) {
      for (td in PsiTreeUtil.findChildrenOfType(psi, SqlTableDefinition::class.java)) {
        val (tableSchema, name) = qualified((td.nameElement?.text ?: td.name), schema)
        val id = "$tableSchema.$name"
        check(dbTables.none { it.id == id }) { "Duplicate table '$id' in SQL imports" }
        check(
          PsiTreeUtil.findChildOfType(
            td,
            com.intellij.psi.PsiErrorElement::class.java
          ) == null
        ) {
          "Could not parse table '$id' in ${psi.name}; check its SQL syntax and the diagram dialect"
        }
        val tableKeys = keys[id].orEmpty()
        val fields =
          PsiTreeUtil.findChildrenOfType(td, SqlColumnDefinition::class.java).map { col ->
            val colName = col.name ?: error("Unnamed column in $id")
            val primary =
              col.primaryKey != null || tableKeys.any { it.first && colName in it.second }
            val unique = col.uniqueKey?.columnsRef?.names()
              ?.toList() == listOf(colName) || tableKeys.any { it.second == listOf(colName) }
            val fullType = col.typeElement?.text ?: extractFullType(colName, col.text)
            ?: col.dasType.toDataType().typeName
            val elementType = fullType.replace(Regex("""(?:\s*\[\s*\d*\s*\])+\s*$"""), "")
              .replace(Regex("""\s+ARRAY\s*$""", RegexOption.IGNORE_CASE), "").trim()
            val values = enums[tableId(elementType, tableSchema)]
            val generatedClause = col.generatedClause?.text
            // Identity is a separate direct PSI clause, not getGeneratedClause().
            val identityPattern = Regex(
              """^GENERATED\s+(ALWAYS|BY\s+DEFAULT)\s+AS\s+IDENTITY\b""",
              RegexOption.IGNORE_CASE
            )
            val identity = col.children.firstNotNullOfOrNull { child ->
              identityPattern.find(child.text)?.groupValues?.get(1)
            }?.uppercase()?.replace(Regex("""\s+"""), " ")
            DbField(
              name = colName,
              isPrimaryKey = primary,
              isForeignKey = col.foreignKey != null,
              isUnique = unique,
              isNotNull = col.isNotNull || primary || identity != null,
              type = fullType,
              default = if (identity == null && generatedClause == null) extractDefault(col.text) else null,
              enumValues = values,
              isArray = elementType != fullType,
              checks = checks[id].orEmpty().filter { colName in it.columns }.map { it.expression },
              identity = identity,
              generated = generatedClause?.takeIf { identity == null },
              note = extractDocComment(col),
              typeCategory = if (values != null) "ENUM" else customTypes[tableId(
                elementType,
                tableSchema
              )]
                ?: if (elementType.substringBefore('(').lowercase() in setOf(
                    "int4range",
                    "int8range",
                    "numrange",
                    "tsrange",
                    "tstzrange",
                    "daterange"
                  )
                ) "RANGE"
                else if (elementType.substringBefore('(')
                    .lowercase() !in KNOWN_BUILTIN_TYPES
                ) "TYPE" else null
            )
          }
        dbTables.add(
          DbTable(
            id = id, schema = tableSchema, name = name, fields = fields,
            indexes = indexes[id], note = extractDocComment(td), checks = checks[id].orEmpty()
          )
        )
      }
      for (fk in PsiTreeUtil.findChildrenOfType(psi, SqlForeignKeyDefinition::class.java)) {
        val sourceId = owner(fk, schema) ?: continue
        val sourceSchema = sourceId.substringBefore('.')
        val targetName = fk.refTableName ?: continue
        val targetSchema = fk.refTableSchema?.takeIf { it.isNotBlank() } ?: sourceSchema
        val targetId = "$targetSchema.$targetName"
        val columns = fk.columnsRef.names().toList()
        val targetColumns = fk.refColumns.names().toList().ifEmpty {
          keys[targetId].orEmpty().firstOrNull { it.first }?.second.orEmpty()
        }
        check(columns.isNotEmpty() && columns.size == targetColumns.size) { "Unresolved foreign key in $sourceId: ${fk.text}" }
        val unique = keys[sourceId].orEmpty().any { it.second.toSet() == columns.toSet() }
        rels.add(
          DbRelationship(
            sourceSchema, sourceId, columns, targetSchema, targetId, targetColumns,
            if (unique) "1:1" else "n:1"
          )
        )
      }
    }
    // External targets (for example Supabase auth.users) keep their reference metadata
    // but are not rendered as dangling edges when their SQL is not imported.
    for (i in dbTables.indices) {
      val table = dbTables[i]
      dbTables[i] = table.copy(fields = table.fields.map { field ->
        val relation = rels.firstOrNull { it.fromTable == table.id && field.name in it.fromColumns }
        if (relation == null) field else field.copy(
          isForeignKey = true, reference = DbReference(
            "->",
            relation.toSchema,
            relation.toTable.substringAfter('.'),
            relation.toColumns[relation.fromColumns.indexOf(field.name)]
          )
        )
      })
    }
    val visibleIds = dbTables.map { it.id }.toSet()
    rels.removeAll { it.fromTable !in visibleIds || it.toTable !in visibleIds }

    // Prefer schema-qualified layout keys; accept unambiguous legacy table names.
    val tablesMap = root.getKeyValueByKey("tables")?.value as? YAMLMapping

    if (tablesMap != null) {
      val yamlLayoutKeys = tablesMap.keyValues.mapNotNull { it.keyText }.toSet()


      for (i in dbTables.indices) {
        val t = dbTables[i]
        val layoutKey = when {
          yamlLayoutKeys.contains(t.id) -> t.id
          dbTables.count { it.name == t.name } == 1 && yamlLayoutKeys.contains(t.name) -> t.name
          else -> null
        } ?: continue

        val lm = tablesMap.getKeyValueByKey(layoutKey)?.value as? YAMLMapping ?: continue
        dbTables[i] = t.copy(
          horizontal = lm.getKeyValueByKey("x")?.valueText?.toIntOrNull(),
          vertical = lm.getKeyValueByKey("y")?.valueText?.toIntOrNull(),
          width = lm.getKeyValueByKey("width")?.valueText?.toIntOrNull(),
          color = lm.getKeyValueByKey("color")?.valueText
        )
      }
    }

    // ── Merge YAML relationship overrides ─────────────────────────────────────
    val relSeq = root.getKeyValueByKey("relationships")?.value as? YAMLSequence
    if (relSeq != null) {
      for (yr in parseYamlRelationships(relSeq)) {
        val idx = rels.indexOfFirst { r ->
          r.fromTable == yr.fromTable && r.fromColumns == yr.fromColumns &&
              r.toTable == yr.toTable && r.toColumns == yr.toColumns
        }
        if (idx >= 0) rels[idx] = rels[idx].copy(
          sourceAnchor = yr.sourceAnchor ?: rels[idx].sourceAnchor,
          targetAnchor = yr.targetAnchor ?: rels[idx].targetAnchor,
          waypoints = yr.waypoints ?: rels[idx].waypoints
        )
        else rels.add(yr)
      }
    }

    val filter = com.abugyda.dbd.configuration.DiagramConfiguration.read(yamlFile).tableFilter
    val included = dbTables.filter { filter.includes(it.id) }
    val includedIds = included.map { it.id }.toSet()
    val importedIds = dbTables.map { it.id }.toSet()
    val visibleTables = included.map { table ->
      val hidden = rels.flatMap { relation ->
        when (table.id) {
          relation.fromTable -> listOf(relation.toTable)
          relation.toTable -> listOf(relation.fromTable)
          else -> emptyList()
        }
      }.filter { it in importedIds && it !in includedIds }.distinct().sorted()
      table.copy(hiddenRelatedTables = hidden)
    }
    return SchemaPayload(
      tables = visibleTables,
      relationships = rels.filter { it.fromTable in includedIds && it.toTable in includedIds },
      projectSettings = DbProject(),
      notes = parseYamlNotes(root),
      groups = (readValue(root.getKeyValueByKey("groups")?.value) as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
      } ?: emptyList(),
      viewport = readMap(
        root,
        "viewport"
      ).mapNotNull { (key, value) -> (value as? Number)?.toDouble()?.let { key to it } }.toMap()
        .takeIf {
          listOf(
            "x",
            "y",
            "zoom"
          ).all { key -> it[key]?.isFinite() == true } && it.getValue("zoom") > 0
        },
      displaySettings = readMap(root, "display"),
      sourceCount = com.abugyda.dbd.configuration.DiagramConfiguration.read(yamlFile).imports.size
    )
  }

  private fun readMap(root: YAMLMapping, key: String): Map<String, Any?> =
    (readValue(root.getKeyValueByKey(key)?.value) as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
      ?: emptyMap()

  private fun readValue(value: org.jetbrains.yaml.psi.YAMLValue?): Any? = when (value) {
    is YAMLMapping -> value.keyValues.associate { it.keyText to readValue(it.value) }
    is YAMLSequence -> value.items.map { readValue(it.value) }
    else -> (value as? org.jetbrains.yaml.psi.YAMLScalar)?.textValue?.let {
      it.toDoubleOrNull() ?: it.toBooleanStrictOrNull() ?: it
    }
  }

  // ── Type helpers ───────────────────────────────────────────────────────────

  private fun extractFullType(colName: String, colText: String): String? {
    val after = colText.removePrefix("\"$colName\"").removePrefix(colName).trimStart()
    if (after.isEmpty()) return null
    return Regex(
      """^([a-zA-Z][a-zA-Z0-9 ]*?(?:\([^)]*\))?\s*(?:\[\])*)\s*(?:NOT\s+NULL|NULL\b|DEFAULT\b|PRIMARY\b|UNIQUE\b|REFERENCES\b|CHECK\b|GENERATED\b|${'$'})""",
      setOf(RegexOption.IGNORE_CASE)
    )
      .find(after)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
  }

  private fun extractDefault(colText: String): String? {
    val raw = DEFAULT_REGEX.find(colText)?.groupValues?.get(1)?.trim() ?: return null
    return if (raw.startsWith("'") && raw.endsWith("'") && raw.length >= 2) raw.substring(
      1,
      raw.length - 1
    ) else raw
  }

  private fun extractInlineEnumValues(colText: String): List<String>? {
    val inner = INLINE_ENUM_REGEX.find(colText)?.groupValues?.get(1) ?: return null
    return inner.split(",").map { it.trim().removeSurrounding("'").removeSurrounding("\"") }
      .filter { it.isNotEmpty() }.ifEmpty { null }
  }

  // ── Doc comment (multi-line) ───────────────────────────────────────────────

  private fun extractDocComment(element: PsiElement): String? {
    val lines = mutableListOf<String>()
    var prev = element.prevSibling
    while (prev != null && prev is PsiWhiteSpace) {
      if (prev.text.contains("\n\n")) return null
      prev = prev.prevSibling
    }
    outer@ while (prev != null) {
      when {
        prev is PsiComment -> {
          val t = prev.text.trim()
          if (t.startsWith("---")) {
            lines.add(0, t.removePrefix("---").trim())
            prev = prev.prevSibling
            while (prev != null && prev is PsiWhiteSpace) {
              if (prev.text.contains("\n\n")) break@outer
              prev = prev.prevSibling
            }
          } else break@outer
        }

        prev is PsiWhiteSpace -> {
          if (prev.text.contains("\n\n")) break@outer; prev = prev.prevSibling
        }

        else -> break@outer
      }
    }
    return if (lines.isEmpty()) null else lines.joinToString(" ")
  }

  // ── YAML notes ─────────────────────────────────────────────────────────────

  private fun parseYamlNotes(root: YAMLMapping): List<DbNote> {
    val seq = root.getKeyValueByKey("notes")?.value as? YAMLSequence ?: return emptyList()
    return seq.items.mapNotNull { item ->
      val m = item.value as? YAMLMapping ?: return@mapNotNull null
      val id =
        m.getKeyValueByKey("id")?.valueText?.removeSurrounding("\"") ?: return@mapNotNull null
      val text = m.getKeyValueByKey("text")?.valueText ?: m.getKeyValueByKey("content")?.valueText
      ?: return@mapNotNull null
      val x = m.getKeyValueByKey("x")?.valueText?.toIntOrNull() ?: 0
      val y = m.getKeyValueByKey("y")?.valueText?.toIntOrNull() ?: 0
      val width = m.getKeyValueByKey("width")?.valueText?.toIntOrNull() ?: 200
      DbNote(
        id = id, name = m.getKeyValueByKey("name")?.valueText ?: id,
        content = text.removeSurrounding("\"").trim(),
        horizontal = x, vertical = y, width = width,
        height = m.getKeyValueByKey("height")?.valueText?.toIntOrNull(),
        color = m.getKeyValueByKey("color")?.valueText?.removeSurrounding("\""),
        target = m.getKeyValueByKey("target")?.valueText?.removeSurrounding("\""),
        targetAnchor = m.getKeyValueByKey("target_anchor")?.valueText?.lowercase()
          ?.takeIf { it in setOf("left", "right", "top", "bottom") }
      )
    }
  }

  // ── YAML relationships ─────────────────────────────────────────────────────

  private fun parseYamlRelationships(seq: YAMLSequence): List<DbRelationship> {
    return seq.items.mapNotNull { item ->
      val m = item.value as? YAMLMapping ?: return@mapNotNull null
      val (fromTable, fromCol) = splitDot(
        m.getKeyValueByKey("source")?.valueText ?: return@mapNotNull null
      ) ?: return@mapNotNull null
      val (toTable, toCol) = splitDot(
        m.getKeyValueByKey("target")?.valueText ?: return@mapNotNull null
      ) ?: return@mapNotNull null
      DbRelationship(
        fromSchema = if ('.' in fromTable) fromTable.substringBefore('.') else "public",
        fromTable = if ('.' in fromTable) fromTable else "public.$fromTable",
        fromColumns = listOf(fromCol),
        toSchema = if ('.' in toTable) toTable.substringBefore('.') else "public",
        toTable = if ('.' in toTable) toTable else "public.$toTable",
        toColumns = listOf(toCol),
        type = m.getKeyValueByKey("type")?.valueText ?: "n:1",
        sourceAnchor = m.getKeyValueByKey("source_anchor")?.valueText?.lowercase()
          ?.takeIf { it in setOf("left", "right") },
        targetAnchor = m.getKeyValueByKey("target_anchor")?.valueText?.lowercase()
          ?.takeIf { it in setOf("left", "right") },
        waypoints = (m.getKeyValueByKey("way_points")?.value as? YAMLSequence)?.let {
          parseWayPoints(
            it
          )
        }
      )
    }
  }

  private fun parseWayPoints(seq: YAMLSequence): List<WayPoint>? {
    val pts = seq.items.mapNotNull { item ->
      val m = item.value as? YAMLMapping ?: return@mapNotNull null
      WayPoint(
        x = m.getKeyValueByKey("x")?.valueText?.toDoubleOrNull() ?: return@mapNotNull null,
        y = m.getKeyValueByKey("y")?.valueText?.toDoubleOrNull() ?: return@mapNotNull null,
        from = m.getKeyValueByKey("from")?.valueText?.lowercase()
          ?.takeIf { it in setOf("source", "target") } ?: return@mapNotNull null
      )
    }
    return pts.ifEmpty { null }
  }

  private fun splitDot(raw: String): Pair<String, String>? {
    val dot = raw.lastIndexOf('.')
    if (dot <= 0 || dot >= raw.length - 1) return null
    return raw.substring(0, dot).trim() to raw.substring(dot + 1).trim()
  }

}
