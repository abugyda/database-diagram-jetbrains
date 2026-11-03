package com.abugyda.dbd.actions

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NewErdYamlActionTest : BasePlatformTestCase() {
  private fun event(action: NewErdYamlAction, context: DataContext) =
    AnActionEvent.createFromAnAction(action, null, ActionPlaces.PROJECT_VIEW_POPUP, context)

  fun testNewMenuWorksWithIdeViewWithoutPsiElement() {
    val directory = myFixture.addFileToProject("diagrams/placeholder.txt", "").containingDirectory
    val action = NewErdYamlAction()
    val view = object : IdeView {
      override fun getDirectories() = arrayOf(directory)
      override fun getOrChooseDirectory(): PsiDirectory = directory
    }
    val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(LangDataKeys.IDE_VIEW, view).build()
    val event = event(action, context)
    assertNull(event.getData(CommonDataKeys.PSI_ELEMENT))
    action.update(event)
    assertTrue(event.presentation.isEnabledAndVisible)
    assertEquals(directory.virtualFile, action.targetDirectory(event))
    assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
  }

  fun testFolderFallbacksAndCancelledDirectoryChoice() {
    val file = myFixture.addFileToProject("diagrams/placeholder.txt", "")
    val directory = file.containingDirectory
    val action = NewErdYamlAction()
    for (context in listOf(
      SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.PSI_ELEMENT, directory).build(),
      SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.VIRTUAL_FILE, directory.virtualFile).build()
    )) {
      val event = event(action, context)
      action.update(event)
      assertTrue(event.presentation.isEnabledAndVisible)
      assertEquals(directory.virtualFile, action.targetDirectory(event))
    }
    val cancelledView = object : IdeView {
      override fun getDirectories() = arrayOf(directory)
      override fun getOrChooseDirectory(): PsiDirectory? = null
    }
    val cancelled = event(action, SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(LangDataKeys.IDE_VIEW, cancelledView).add(CommonDataKeys.VIRTUAL_FILE, directory.virtualFile).build())
    assertNull(action.targetDirectory(cancelled))
    val noFolder = event(action, SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile).build())
    action.update(noFolder)
    assertFalse(noFolder.presentation.isEnabledAndVisible)
    val noProject = event(action, SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, directory.virtualFile).build())
    action.update(noProject)
    assertFalse(noProject.presentation.isEnabledAndVisible)
  }

  fun testRegisteredNewMenuIncludesExplicitErdYamlEntry() {
    val manager = ActionManager.getInstance()
    val action = manager.getAction("com.abugyda.dbd.NewErdYaml") as NewErdYamlAction
    assertEquals("ERD Diagram (.erd.yaml)", action.templatePresentation.text)
    val group = manager.getAction("com.abugyda.dbd.NewFileGroup") as ActionGroup
    assertTrue(group.getChildren(null).contains(action))
  }
}
