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
import com.intellij.psi.*;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaFxConfiguratorService {
    private final Project myProject;
    private boolean isJavaFxProject = false;
    private boolean isProjectConfigured = false;
    private PsiClass mainClass;
    private Module mainClassModule;

    public JavaFxConfiguratorService(Project project) {
        myProject = project;
    }

    //If this is a JavaFX project, run configured tasks on it
    public void detectProjectType() {
        AppSettingsState settings = AppSettingsState.getInstance();
        if (settings.javaFxPath.isEmpty())
            // Plugin not configured
            return;

        findFxml(myProject.getBasePath());
        if (!isJavaFxProject && settings.detectNonFxml)
            isJavaFxProject = (findMainClass() != null);

        if (isJavaFxProject) {
            System.out.println("JavaFX Helper: Project " + myProject.getName() + " is a JavaFX project");
            findMainClass();
            if (mainClass == null) {
                System.out.println("JavaFX Helper: Couldn't find main class on project " + myProject.getName());
                return;
            }
            addJavaFxLibrary();

            String vmParameters;
            if (System.getProperty("os.name").contains("Windows"))
                vmParameters = "--module-path \"" + settings.javaFxPath + "\" --add-modules javafx.controls,javafx.fxml";
            else
                vmParameters = "--module-path " + settings.javaFxPath + " --add-modules javafx.controls,javafx.fxml";

            if (!changeRunConfiguration(vmParameters))
                DumbService.getInstance(myProject).runWhenSmart(() -> addRunConfiguration(vmParameters));

            if (settings.fixBrokenSdk)
                fixBrokenSdk();
        }
    }

    // If project SDK is invalid, set the first valid Java SDK
    private void fixBrokenSdk() {
        Sdk projectSDK = ProjectRootManager.getInstance(myProject).getProjectSdk();
        if (projectSDK == null) {
            Optional<Sdk> existingSdk = Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
                    .filter(sdk -> sdk.getSdkType() instanceof JavaSdk)
                    .findFirst();

            if (existingSdk.isPresent()) {
                ApplicationManager.getApplication().runWriteAction(() ->
                    ProjectRootManager.getInstance(myProject).setProjectSdk(existingSdk.get()));
                System.out.println("JavaFX Helper: SDK was null, becomes " + existingSdk.get());
            }
            else
                System.out.println("JavaFX Helper: SDK is null, no Java SDK found");
        }
         else
            System.out.println("JavaFX Helper: SDK is " + projectSDK);
    }

    // Add new run configuration to project
    private void addRunConfiguration(String vmParameters) {
        RunManager runManager = RunManager.getInstance(myProject);

        ApplicationManager.getApplication().runWriteAction(() -> {
            ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration(mainClass.getName(), myProject, ApplicationConfigurationType.getInstance());
            applicationConfiguration.setMainClass(mainClass);
            applicationConfiguration.setWorkingDirectory(myProject.getBasePath());
            applicationConfiguration.setVMParameters(vmParameters);
            applicationConfiguration.setModule(findModule());
            RunnerAndConfigurationSettings configuration = runManager.createConfiguration(applicationConfiguration, applicationConfiguration.getFactory());
            runManager.addConfiguration(configuration);
            RunManager.getInstance(myProject).setSelectedConfiguration(configuration);
        });
        System.out.println("JavaFX Helper: Created new run configuration");
    }

    // Find Main class
    private PsiClass findMainClass() {
        if (mainClass != null) return mainClass;
        PsiManager manager = PsiManager.getInstance(myProject);
        //Pattern p = Pattern.compile(".*\\s+extends\\s+Application.*");
        for (PsiClass psiClass : AllClassesSearch.search(GlobalSearchScope.projectScope(myProject), myProject)) {
            //Matcher m = p.matcher(psiClass.getText());
            //if (m.matches()) {
            if (psiClass.getText().contains("extends Application")) {
                System.out.println("JavaFX Helper: Found main class " + psiClass.getQualifiedName());
                mainClass = psiClass;
                VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiClass);
                final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
                mainClassModule = fileIndex.getModuleForFile(virtualFile);
                return psiClass;
            }
        }
        return null;
    }

    // Update existing run configuration, add VM options
    private boolean changeRunConfiguration(String vmParameters) {
        for (RunnerAndConfigurationSettings runConfigSettings : RunManager.getInstance(myProject).getAllSettings()) {
            if (runConfigSettings.getClass().equals(mainClass)) {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    ApplicationConfiguration appConf = (ApplicationConfiguration) runConfigSettings.getConfiguration();
                    appConf.setVMParameters(vmParameters);
                    RunManager.getInstance(myProject).setSelectedConfiguration(runConfigSettings);
                });
                System.out.println("JavaFX Helper: Changed existing run configuration");
                return true;
            }
        }
        return false;
    }

    // Add the configured JavaFX library to project
    private void addJavaFxLibrary() {
        LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
        for (Library l : projectLibraryTable.getLibraries())
            if (l.getName().equals("javafx")) {
                System.out.println("JavaFX Helper: JavaFX library already added to project");
                return;
            }

        final LibraryTable.ModifiableModel projectLibraryModel = projectLibraryTable.getModifiableModel();
        AppSettingsState settings = AppSettingsState.getInstance();

        Library library = projectLibraryModel.createLibrary("javafx");
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        String pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, settings.javaFxPath);
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(pathUrl);
        final @Nullable Module module = findModule();
        System.out.println("JavaFX Helper: Module is " + module.getModuleFilePath());

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
                    if (module != null) {
                        ModuleRootModificationUtil.addDependency(module, library);
                        System.out.println("JavaFX Helper: Added JavaFX library to project");
                    }
                }
            });
        }
    }

    // Find Module (.iml file) that we have to update
    private Module findModule() {
        if (mainClassModule != null)
            return mainClassModule;

        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        if (mainClass != null) {
            VirtualFile virtualFile = PsiUtilCore.getVirtualFile(mainClass);
            if (virtualFile != null) {
                mainClassModule = fileIndex.getModuleForFile(virtualFile);
                return mainClassModule;
            }
        }

        // Couldn't find main class
        // Give us any class at all
        for (File file: new File(myProject.getBasePath()).listFiles()) {
            if (file.getName().toLowerCase().contains(".java")) {
                String pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getAbsolutePath());
                mainClassModule = fileIndex.getModuleForFile(VirtualFileManager.getInstance().findFileByUrl(pathUrl));
                return mainClassModule;
            }
        }
        System.out.println("JavaFX Helper: Couldn't find module");
        return null;
    }

    // Look for FXML files in project - easy and quick way to detect JavaFX projects
    // without overhead of PSI
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
