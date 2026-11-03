package com.abugyda.dbd.builders

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/** Directory changes can add/remove many SQL files without individual SQL events. */
internal fun affectsSqlSources(event: VFileEvent): Boolean =
  event.path.endsWith(".sql", true) || event.file?.isDirectory == true ||
      (event is VFileCreateEvent && event.isDirectory) ||
      (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME &&
          event.oldValue.toString().endsWith(".sql", true))
