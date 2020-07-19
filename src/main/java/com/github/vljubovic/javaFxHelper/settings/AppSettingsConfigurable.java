package com.github.vljubovic.javaFxHelper.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * Provides controller functionality for application settings.
 */
public class AppSettingsConfigurable implements Configurable {
    private AppSettingsComponent mySettingsComponent;

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "JavaFX Helper Plugin";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettingsComponent = new AppSettingsComponent();
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        AppSettingsState settings = AppSettingsState.getInstance();
        boolean modified = !mySettingsComponent.getJavaFxPathText().equals(settings.javaFxPath);
        modified |= mySettingsComponent.getNonFxml() != settings.detectNonFxml;
        modified |= mySettingsComponent.getBrokenSdk() != settings.fixBrokenSdk;
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        boolean found = false;
        for (File file: new File(mySettingsComponent.getJavaFxPathText()).listFiles()) {
            if (file.getName().equals("lib") && file.isDirectory()) {
                mySettingsComponent.setJavaFxPathText(file.getAbsolutePath());
                apply();
                return;
            }
            if (file.getName().equals("javafx.base.jar")) {
                found = true;
                break;
            }
        }
        if (!found) {
            Project p = null;
            Messages.showMessageDialog(p, "This doesn't appear to be a JavaFX library path. Please check again", "Is this JavaFX path?", Messages.getWarningIcon());
        }

        AppSettingsState settings = AppSettingsState.getInstance();
        settings.javaFxPath = mySettingsComponent.getJavaFxPathText();
        settings.detectNonFxml = mySettingsComponent.getNonFxml();
        settings.fixBrokenSdk = mySettingsComponent.getBrokenSdk();
    }

    @Override
    public void reset() {
        AppSettingsState settings = AppSettingsState.getInstance();
        mySettingsComponent.setJavaFxPathText(settings.javaFxPath);
        mySettingsComponent.setNonFxml(settings.detectNonFxml);
        mySettingsComponent.setBrokenSdk(settings.fixBrokenSdk);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

}