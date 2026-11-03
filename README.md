# Database Diagram — IntelliJ Plugin

An Entity-Relationship Diagram (ERD) previewer and editor for IntelliJ-based IDEs.
Open any `.erd.yaml` file to see a live, interactive diagram of your SQL schema.

Developed by **Abugyda**. Kotlin packages and plugin, action, and settings
registration IDs use `com.abugyda.dbd`.

---

## Features

- **Split editor** — YAML source on the left, interactive diagram on the right
- **Live updates** — diagram re-renders as you type in the `.erd.yaml` or any imported `.sql` file
- **Drag & drop layout** — move and resize tables; positions are written back to the YAML automatically
- **Relationship lines** — infers FK relationships from `REFERENCES` clauses; supports explicit routing with `way_points`, `source_anchor`, and `target_anchor`
- **Self-referencing tables** — FK loops are drawn on the left side to avoid overlap
- **Semantic zoom** — zooming out switches to a compact overlay view showing table name and column count
- **Custom colours** — per-table and per-note colour picker with Material Design palette
- **Doc-comment tooltips** — `--- comment` lines above columns/tables appear as tooltips on hover
- **Sticky notes** — annotate the diagram with Markdown-rendered notes, optionally with callout arrows pointing to specific tables or columns
- **Line style** — Curve, Rectilinear, RoundRectilinear, Oblique, RoundOblique
- **Theme** — System / Light / Dark, detected automatically from the IDE Look & Feel
- **"New" menu** — right-click a directory → New → ERD Diagram or SQL Schema File

---

## Requirements

| Requirement | Version |
|---|---|
| IntelliJ IDEA (or any IntelliJ-based IDE) | 2025.2.4+ (build 252+) |
| JDK | 21 |
| Kotlin | 2.1.20 |
| Node.js (for building the web UI) | 22 |

---

## Project structure

```
database-diagram-jetbrains/
├── build.gradle.kts                  Main Gradle build
├── gradle.properties                 Shared Gradle settings
├── tools/dump_feature.py             Optional source-dump utility
├── build/generated/webResources/web/ Built web UI (generated, gitignored)
│
└── src/main/
    ├── kotlin/com/abugyda/dbd/
    │   ├── actions/
    │   │   ├── NewErdYamlAction.kt   "New → ERD Diagram" menu action
    │   │   └── NewSqlSchemaAction.kt "New → SQL Schema File" menu action
    │   ├── builders/
    │   │   └── ErdDataBuilder.kt     Parses SQL PSI + YAML into SchemaPayload
    │   ├── editor/
    │   │   ├── SchemaSplitEditorProvider.kt  Registers the split editor
    │   │   ├── SchemaPreviewFileEditor.kt    Preview panel + bridge callbacks
    │   │   └── ErdYamlUpdater.kt             Writes positions/colours back to YAML
    │   ├── settings/
    │   │   ├── DatabaseDiagramSettings.kt    Persistent settings state
    │   │   └── DatabaseDiagramConfigurable.kt  IDE Settings panel
    │   ├── templates/
    │   │   └── ErdFileTemplateGroup.kt       Registers .ft file templates
    │   └── webview/
    │       ├── WebviewBridge.kt              Kotlin ↔ JS data model + message types
    │       ├── WebviewPanel.kt               JCEF browser host
    │       └── WebviewSchemeHandler.kt       Custom scheme for resource loading
    │
    └── resources/
        ├── META-INF/plugin.xml
        └── fileTemplates/
            ├── ErdYaml.ft            Default .erd.yaml template
            └── SqlSchema.ft          Default .sql template
```

---

Schema discovery and parsing run in cancellable background read actions after
indexing and document commits. Startup, manual refresh, and SQL/YAML edits share
this pipeline. Older requests expire when a newer change arrives, and closing an
editor cancels its pending work. Presentation saves cancel outdated reads without
reloading SQL. The startup timeout queues through the IDE dispatcher and does not
commit documents from a Swing timer callback.

**IDE Settings → Tools → Database Diagram** has four tabs: General, Fonts, Grid
and Snapping, and Descriptions. Fonts retains the 10–24 px global default (12 px)
and provides independent 8–48 px overrides for table headers/descriptions, column
names/descriptions/types, note headers/body, group labels, overview text, details
and relationship labels. A value of 0 inherits the existing text hierarchy.
Apply updates open diagrams; Reset discards unapplied edits. Fonts are global
preferences and are not written into diagram YAML.

**Editor → Color Scheme → Database Diagram** configures canvas, grid, surfaces,
borders, table and column text, indexed rows, key icons, notes, group labels and
relationship lines. Light and dark scheme defaults are supplied. Text entries,
including NN and UQ badges, support foreground/background colors and bold/italic
styles. A compact tagged schema sample uses the standard IDE preview. Saved
per-table and per-note colors take precedence over scheme default backgrounds;
default header/note foregrounds retain automatic contrast for those card colors.
Scheme changes update open diagrams and use standard scheme import/export.

Type badges ENM, REC, DOM, RNG, and ARR and behavior markers C (check),
I (identity), and G (computed generated column) each have independent foreground,
background, bold, and italic settings in the Color Scheme page. Both light and
dark defaults are included. The SQL bridge supplies array element categories,
check expressions (including table-level checks and ALTER TABLE checks), identity
mode, and generated-column SQL. Display shortening affects labels only; original
SQL is retained in tooltips and constraints are listed in table details.

## Visual diagram setup

Right-click a project folder and choose **New → ERD Diagram** to open setup.
Choose the diagram name, then add SQL entrypoints with IntelliJ's project file
chooser. Multiple files can be selected; included `\i` and `\ir` files are
resolved automatically. Select the IDE dialect mapping or PostgreSQL, and supply
optional schema overrides for sources using unqualified table names.

**Preview tables** validates the sources without creating a file. The Tables tab
provides a searchable checkbox tree grouped by schema, **Select shown**,
**Clear shown**, and **Include related tables**. Checked tables are displayed.

- **All tables except unchecked** includes newly discovered tables automatically.
- **Only checked tables** keeps newly discovered tables hidden until selected.

Create opens the visual diagram. Use the folder icon in its toolbar, or
**Configure Diagram…** in the file/editor context menu, to change sources and
visible tables later. Empty diagrams offer **Add SQL sources**; import errors
also offer configuration. Tables with hidden imported neighbors have an indicator
that opens setup with those neighbors checked, ready to apply.

Configuration changes are undoable and preserve layout, notes, groups, display
preferences and other YAML settings. Hidden tables keep their saved positions
and colors. Relationships are drawn only when both endpoint tables are visible;
foreign-key reference metadata remains available when a target is hidden.
SQL files are read to discover the schema. Configuration updates only `.erd.yaml`.

The generated filter is stored alongside imports:

```yaml
schema:
  dialect: postgresql
  imports:
    - path: ../public.sql
      schema: public
  table_filter:
    mode: exclude
    tables: [public.internal_events]
```

`exclude` lists hidden tables; `include` lists the tables to show. IDs are
schema-qualified. Existing diagrams without a filter continue to show all tables.

---

## Building

### Prerequisites

- The standalone web project must be accessible (see **Web UI** below)
- Set `localIdePath` in `build.gradle.kts` to your local IntelliJ installation, or use `intellijIdea("version")` to download one

### Web UI location

The build reads the web project path from (highest priority first):

1. `WEB_PROJECT_DIR` environment variable
2. `webProjectDir` in `gradle.properties`
3. Sibling directory default: `../database-diagram-web`

Example `gradle.properties` (keep machine-specific path overrides local):
```properties
webProjectDir=../database-diagram-web
kotlin.daemon.jvmargs=-Xmx2g -XX:+UseG1GC
org.gradle.jvmargs=-Xmx1500m -XX:+UseG1GC
```

### Commands

```bash
# Build the plugin JAR (also builds the web UI)
./gradlew buildPlugin

# Build the web UI and sync it to build/generated/webResources/web
./gradlew copyWebDist

# Run the plugin in a sandboxed IDE instance
./gradlew runIde

# Type-check the Kotlin sources
./gradlew compileKotlin
```

---

## `.erd.yaml` format

```yaml
schema:
  imports:
    - "schema.sql"          # Relative paths to SQL files

# Visual layout — managed automatically by dragging tables
tables:
  users:
    x: 100
    y: 150
    width: 260
    color: "#3b82f6"        # Optional header colour (hex)

# Relationship routing overrides (optional)
relationships:
  - source: orders.user_id
    target: users.id
    source_anchor: right    # left | right
    target_anchor: left
    way_points:
      - { x: 50, y: 0, from: source }
      - { x: 50, y: 0, from: target }

# Diagram annotations
notes:
  - id: "note_001"
    text: |
      **Important:** All timestamps are stored in UTC.
    x: 1300
    y: 150
    width: 250
    color: "#fffde7"
    target: "orders.user_id"   # Optional callout arrow
    target_anchor: right
```

---

## IDE Settings

**Settings → Tools → Database Diagram**

| Setting | Description |
|---|---|
| Default layout | Editor / Editor and Preview / Preview |
| Line style | Curve / Rectilinear / RoundRectilinear / Oblique / RoundOblique |
| Show grid | Toggle grid background |
| Grid size | Grid cell size in pixels |
| Theme | System / Light / Dark |
| Show table notes | Toggle `--- comment` display in table headers |
| Show field notes | Toggle `--- comment` display on columns |
| Max table note lines | Line clamp for table notes (0 = unlimited) |
| Max field note lines | Line clamp for field notes (0 = unlimited) |

---

## SQL doc-comment syntax

Place a `---` comment on the line directly above a `CREATE TABLE` or column definition to attach a note that appears in the diagram:

```sql
--- Stores authenticated user accounts.
CREATE TABLE users (
    id          SERIAL      PRIMARY KEY,

    --- The user's display name shown in the UI.
    display_name VARCHAR(120) NOT NULL,

    --- Role of this account; determines which workflows are available.
    --- See public.account_type enum for valid values.
    account_type account_type NOT NULL DEFAULT 'individual'
);
```

Multi-line `---` blocks are joined into a single note with a space.

---

## Architecture

```
IDE (Kotlin)
  SchemaSplitEditorProvider
    └─ SchemaPreviewFileEditor          Disposable, owns the lifecycle
         ├─ ErdDataBuilder              SQL PSI + YAML → SchemaPayload
         ├─ ErdYamlUpdater              Writes x/y/colour back to YAML
         └─ WebviewPanel                Hosts the JCEF browser
              └─ window.postMessage()  ──► JS: Bridge.ts
                                               └─ DiagramApplication
                                                    └─ DiagramController
```

Messages are JSON objects tagged with a `type` discriminator.
`WebviewBridge.kt` defines all shared data classes (tables, fields, relationships, notes) and both sealed class hierarchies (`Server` and `Client`).

---

## Contributing

1. Fork and clone the repo
2. Open in IntelliJ IDEA
3. Set `localIdePath` in `build.gradle.kts`
4. Run `./gradlew runIde` to launch a sandbox IDE with the plugin loaded
5. The web UI hot-reloads via `npm run dev` in the web project — see the web project README for details

## Public/Catalog diagram integration

A diagram can import schema entrypoints with an explicit default schema:

```yaml
schema:
  dialect: postgresql
  imports:
    - path: ../public.sql
      schema: public
    - path: ../../catalog/catalog.sql
      schema: catalog
```

The explicit dialect uses the IDE PostgreSQL parser without changing project SQL mappings.
Omit it to use the configured IDE dialect. Paths are relative to the diagram. Nested `\i` and `\ir` include paths are
resolved relative to the containing SQL file, matching this project's pgschema
layout. This is static source loading; SQL is never executed. Missing imports and
cycles are reported in the preview. Shell commands, variables, and `\cd` scripts
are not interpreted; import the per-schema entrypoints instead of database.sql.

The existing IntelliJ SQL PSI model supplies tables, columns, primary/unique keys,
foreign keys (including composite keys and ALTER TABLE constraints), and indexes.
IDs and layout keys are schema-qualified (`public.profiles`). Unqualified legacy
layout keys are accepted when the table name is unambiguous. External foreign-key
targets such as auth.users remain in column reference details without dangling edges.

The webview READY handshake sends the initial schema after the listener is installed.
The Reset icon refreshes SQL in the IDE, flushing pending layout saves first. Parsing
errors retain the previous diagram and appear as a dismissible message. Newly added
tables appear beside the positioned tables rather than resetting the entire layout.

Presentation changes use SAVE_PRESENTATION and one undoable YAML update. Positions,
widths, note sizes/colors, groups, viewport, and display preferences are stored in the
.erd.yaml file. The packaged JS, CSS and fonts load through a local intercepted HTTPS
origin; no web server is needed. Development browser storage remains separate.

Run integration tests with `./gradlew test --no-configuration-cache`. Tests cover
nested/missing/circular imports, composite keys, cross-schema ALTER constraints,
YAML persistence, and a frozen Adouli Public/Catalog SQL fixture. Build an installable
plugin with `./gradlew buildPlugin` and open
`adouli_database/database/public/diagrams/overview.erd.yaml` in the sandbox IDE.

If another sandbox IDE is running, use `-PdiagramSandbox=idea-sandbox-integration`
to build/test without overwriting its locked plugin JARs.
