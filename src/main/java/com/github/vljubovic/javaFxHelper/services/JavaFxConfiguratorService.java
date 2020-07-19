package com.github.vljubovic.javaFxHelper.services;

import com.github.vljubovic.javaFxHelper.settings.AppSettingsState;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Nullable;

import javax.naming.directory.SearchControls;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;

public class JavaFxConfiguratorService {
    private final Project myProject;
    private boolean isJavaFxProject = false;
    private boolean isProjectConfigured = false;

    public JavaFxConfiguratorService(Project project) {
        myProject = project;
    }

    private void configureProject() {
        isProjectConfigured = true;
        Messages.showMessageDialog(myProject, "This is a message" + myProject.getName(), "My plugin message", Messages.getInformationIcon());
    }

    public void detectProjectType() {
        AppSettingsState settings = AppSettingsState.getInstance();
        if (settings.javaFxPath.isEmpty())
            // Plugin not configured
            return;

        findFxml(myProject.getBasePath());
        if (!isJavaFxProject && settings.detectNonFxml)
            isJavaFxProject = (findMainClass() != null);

        if (isJavaFxProject) {
            //Messages.showMessageDialog(myProject, "Project " + myProject.getName() + " is a JavaFX project", "JavaFX Helper", Messages.getInformationIcon());
            addJavaFxLibrary();

            String vmParameters;
            if (System.getProperty("os.name").contains("Windows"))
                vmParameters = "--module-path \"" + settings.javaFxPath + "\" --add-modules javafx.controls,javafx.fxml";
            else
                vmParameters = "--module-path " + settings.javaFxPath + " --add-modules javafx.controls,javafx.fxml";

            if (!changeRunConfiguration(vmParameters))
                addRunConfiguration(vmParameters);

            if (settings.fixBrokenSdk)
                fixBrokenSdk();
        }
    }

    private void fixBrokenSdk() {
        Sdk projectSDK = ProjectRootManager.getInstance(myProject).getProjectSdk();
        if (projectSDK == null) {
            Optional<Sdk> existingSdk = Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
                    // If a JDK belongs to this particular `pantsExecutable`, then its name will contain the path to Pants.
                    .filter(sdk -> sdk.getSdkType() instanceof JavaSdk)
                    .findFirst();

            if (existingSdk.isPresent()) {
                ProjectRootManager.getInstance(myProject).setProjectSdk(existingSdk.get());
                //Messages.showMessageDialog(myProject, "SDK was null, becomes " + existingSdk.get(), "JavaFX Helper", Messages.getInformationIcon());
            }
            //else
            //    Messages.showMessageDialog(myProject, "SDK is null, no Java SDK found", "JavaFX Helper", Messages.getInformationIcon());
        }
        //else
         //   Messages.showMessageDialog(myProject, "SDK is " + projectSDK, "JavaFX Helper", Messages.getInformationIcon());
    }

    private void addRunConfiguration(String vmParameters) {
        RunManager runManager = RunManager.getInstance(myProject);
        PsiClass mainClass = findMainClass();
        if (mainClass == null) return;

        ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration(mainClass.getName(), myProject, ApplicationConfigurationType.getInstance());
        applicationConfiguration.setMainClass(mainClass);
        applicationConfiguration.setWorkingDirectory(myProject.getBasePath());
        applicationConfiguration.setVMParameters(vmParameters);
        RunnerAndConfigurationSettings configuration = runManager.createConfiguration(applicationConfiguration, applicationConfiguration.getFactory());
        runManager.addConfiguration(configuration);
        RunManager.getInstance(myProject).setSelectedConfiguration(configuration);
        //Messages.showMessageDialog(myProject, "Created new run configuration", "JavaFX Helper", Messages.getInformationIcon());
    }

    private PsiClass findMainClass() {
        PsiManager manager = PsiManager.getInstance(myProject);
        for (PsiClass psiClass : AllClassesSearch.search(GlobalSearchScope.projectScope(myProject), myProject)) {
            if (psiClass.getText().contains("extends Application"))
                return psiClass;
        }
        return null;
    }

    private boolean changeRunConfiguration(String vmParameters) {
        for (RunnerAndConfigurationSettings runConfigSettings : RunManager.getInstance(myProject).getAllSettings()) {
            if (runConfigSettings.getName().equals("Main")) {
                ApplicationConfiguration appConf = (ApplicationConfiguration)runConfigSettings.getConfiguration();
                appConf.setVMParameters(vmParameters);
                RunManager.getInstance(myProject).setSelectedConfiguration(runConfigSettings);
                Messages.showMessageDialog(myProject, "Changed run configuration", "JavaFX Helper", Messages.getInformationIcon());
                return true;
            }
        }
        return false;
    }

    private void addJavaFxLibrary() {
        LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
        for (Library l : projectLibraryTable.getLibraries())
            if (l.getName().equals("javafx")) return;

        final LibraryTable.ModifiableModel projectLibraryModel = projectLibraryTable.getModifiableModel();
        AppSettingsState settings = AppSettingsState.getInstance();

        Library library = projectLibraryModel.createLibrary("javafx");
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        String pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, settings.javaFxPath);
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(pathUrl);
        final @Nullable Module module = findModule();

        if (file != null) {
            libraryModel.addRoot(file, OrderRootType.CLASSES);
            libraryModel.addRoot(file, OrderRootType.SOURCES);
            libraryModel.addRoot(file, NativeLibraryOrderRootType.getInstance());
            libraryModel.addJarDirectory(file, false);
            libraryModel.addJarDirectory(file, false, OrderRootType.SOURCES);
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                    libraryModel.commit();
                    projectLibraryModel.commit();
                    if (module != null)
                        ModuleRootModificationUtil.addDependency(module, library);
                    //else
                        //Messages.showMessageDialog(myProject, "Module is null", "JavaFX Helper", Messages.getInformationIcon());
                }
            });
        }
    }

    private Module findModule() {
        PsiClass mainClass = findMainClass();
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        if (mainClass != null) {
            return fileIndex.getModuleForFile(PsiUtilCore.getVirtualFile(mainClass));
        }
        // Couldn't find main class
        // Give us any class at all
        for (File file: new File(myProject.getBasePath()).listFiles()) {
            if (file.getName().toLowerCase().contains(".java")) {
                String pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getAbsolutePath());
                return fileIndex.getModuleForFile(VirtualFileManager.getInstance().findFileByUrl(pathUrl));
            }
        }
        return null;
    }

    private void findFxml(String path) {
        for (File file: new File(path).listFiles()) {
            if (file.getName().toLowerCase().contains(".fxml")) {
                isJavaFxProject = true;
                break;
            }
        }
        for (File file: new File(path).listFiles()) {
            if (isJavaFxProject) break;
            if (file.isDirectory())
                findFxml(file.getAbsolutePath());
        }
    }
}
