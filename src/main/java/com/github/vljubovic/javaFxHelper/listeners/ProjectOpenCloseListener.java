// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.vljubovic.javaFxHelper.listeners;

import com.github.vljubovic.javaFxHelper.services.JavaFxConfiguratorService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * Listener to detect project open and close.
 * Depends on org.intellij.sdk.maxOpenProjects.ProjectCountingService
 */
public class ProjectOpenCloseListener implements ProjectManagerListener {

  /**
   * Invoked on project open.
   *
   * @param project opening project
   */
  @Override
  public void projectOpened(@NotNull Project project) {
    // Ensure this isn't part of testing
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    JavaFxConfiguratorService service = project.getService(JavaFxConfiguratorService.class);
    DumbService.getInstance(project).runWhenSmart(service::detectProjectType);
  }

}
