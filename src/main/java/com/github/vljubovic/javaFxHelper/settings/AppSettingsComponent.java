package com.github.vljubovic.javaFxHelper.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

/**
 * Supports creating and managing a JPanel for the Settings Dialog.
 */
public class AppSettingsComponent {
    private final JPanel myMainPanel;
    private final TextFieldWithBrowseButton myJavaFxPathText = new TextFieldWithBrowseButton();
    private final JBCheckBox myNonFxml = new JBCheckBox("Detect JavaFX apps without FXML");
    private final JBCheckBox myBrokenSdk = new JBCheckBox("Fix broken SDK");

    public AppSettingsComponent() {
        myJavaFxPathText.addBrowseFolderListener("Select path to JavaFX library", null, null, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Enter JavaFX path: "), myJavaFxPathText, 1, false)
                .addComponent(myNonFxml, 1)
                .addComponent(myBrokenSdk, 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return myJavaFxPathText;
    }

    @NotNull
    public String getJavaFxPathText() {
        return myJavaFxPathText.getText();
    }

    public void setJavaFxPathText(@NotNull String newText) {
        myJavaFxPathText.setText(newText);
    }

    public boolean getNonFxml() {
        return myNonFxml.isSelected();
    }

    public void setNonFxml(boolean newStatus) {
        myNonFxml.setSelected(newStatus);
    }

    public boolean getBrokenSdk() {
        return myBrokenSdk.isSelected();
    }

    public void setBrokenSdk(boolean newStatus) {
        myBrokenSdk.setSelected(newStatus);
    }

}