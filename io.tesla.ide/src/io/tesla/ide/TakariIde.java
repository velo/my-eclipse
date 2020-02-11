package io.tesla.ide;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;
import org.eclipse.ui.internal.ide.ChooseWorkspaceDialog;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.ide.StatusUtil;
import org.eclipse.ui.internal.ide.application.IDEApplication;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

public class TakariIde extends IDEApplication {


  /**
   * The name of the folder containing metadata information for the workspace.
   */
  public static final String METADATA_FOLDER = ".metadata"; //$NON-NLS-1$

  private static final String VERSION_FILENAME = "version.ini"; //$NON-NLS-1$

  // Use the branding plug-in of the platform feature since this is most likely
  // to change on an update of the IDE.
  private static final String WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME = "org.eclipse.platform"; //$NON-NLS-1$
  private static final Version WORKSPACE_CHECK_REFERENCE_BUNDLE_VERSION;
  static {
    Bundle bundle = Platform.getBundle(WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME);
    WORKSPACE_CHECK_REFERENCE_BUNDLE_VERSION = bundle != null ? bundle.getVersion() : null/* not installed */;
  }

  private static final String WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME_LEGACY = "org.eclipse.core.runtime"; //$NON-NLS-1$
  private static final String WORKSPACE_CHECK_LEGACY_VERSION_INCREMENTED = "2"; //$NON-NLS-1$   legacy version=1

  private static final String PROP_EXIT_CODE = "eclipse.exitcode"; //$NON-NLS-1$

  /**
   * A special return code that will be recognized by the launcher and used to restart the workbench.
   */
  private static final Integer EXIT_RELAUNCH = new Integer(24);

  /**
   * A special return code that will be recognized by the PDE launcher and used to show an error dialog if the workspace is locked.
   */
  private static final Integer EXIT_WORKSPACE_LOCKED = new Integer(15);

  /**
   * The ID of the application plug-in
   */
  public static final String PLUGIN_ID = "org.eclipse.ui.ide.application"; //$NON-NLS-1$

  /**
   * Creates a new IDE application.
   */
  public TakariIde() {
    // There is nothing to do for IDEApplication
  }


  //@Override
  public Object startX(IApplicationContext context) throws Exception {

    final Display display = PlatformUI.createDisplay();
    final TakariWorkbenchAdvisor advisor = new TakariWorkbenchAdvisor();
    display.addListener(SWT.OpenDocument, advisor.getOpenDocumentHandler());

    // FIXME: Check unhandled arguments for a pom.xml file and treat like an
    // OpenDocument
    for (String arg : (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS)) {
      File f = new File(arg);
      if (f.exists()) {
        if ("pom.xml".equals(f.getName())) {
          Event e = new Event();
          e.text = arg;
          advisor.getOpenDocumentHandler().handleEvent(e);
        } else if (f.isDirectory() && (f = new File(f, "pom.xml")).exists()) {
          Event e = new Event();
          e.text = f.getPath();
          advisor.getOpenDocumentHandler().handleEvent(e);
        }
      }
    }

    try {
      int returnCode = PlatformUI.createAndRunWorkbench(display, advisor);
      if (returnCode == PlatformUI.RETURN_RESTART) {
        return IApplication.EXIT_RESTART;
      }
      return IApplication.EXIT_OK;
    } finally {
      Location loc = Platform.getInstanceLocation();
      if (loc != null) {
        loc.release();
      }
      display.dispose();
    }

  }


  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext context)
   */
  @Override
  public Object start(IApplicationContext appContext) throws Exception {

    //Display display = createDisplay();
    // processor must be created before we start event loop
    //DelayedEventsProcessor processor = new DelayedEventsProcessor(display);

    final Display display = PlatformUI.createDisplay();
    final TakariWorkbenchAdvisor advisor = new TakariWorkbenchAdvisor();
    display.addListener(SWT.OpenDocument, advisor.getOpenDocumentHandler());

    // FIXME: Check unhandled arguments for a pom.xml file and treat like an
    // OpenDocument
    for (String arg : (String[]) appContext.getArguments().get(IApplicationContext.APPLICATION_ARGS)) {
      File f = new File(arg);
      if (f.exists()) {
        if ("pom.xml".equals(f.getName())) {
          Event e = new Event();
          e.text = arg;
          advisor.getOpenDocumentHandler().handleEvent(e);
        } else if (f.isDirectory() && (f = new File(f, "pom.xml")).exists()) {
          Event e = new Event();
          e.text = f.getPath();
          advisor.getOpenDocumentHandler().handleEvent(e);
        }
      }
    }

    try {

      // look and see if there's a splash shell we can parent off of
      Shell shell = WorkbenchPlugin.getSplashShell(display);
      if (shell != null) {
        // should should set the icon and message for this shell to be the
        // same as the chooser dialog - this will be the guy that lives in
        // the task bar and without these calls you'd have the default icon
        // with no message.
        shell.setText(ChooseWorkspaceDialog.getWindowTitle());
        shell.setImages(Window.getDefaultImages());
      }

      Object instanceLocationCheck = checkInstanceLocation(shell, appContext.getArguments());
      if (instanceLocationCheck != null) {
        WorkbenchPlugin.unsetSplashShell(display);
        appContext.applicationRunning();
        return instanceLocationCheck;
      }

      // create the workbench with this advisor and run it until it exits
      // N.B. createWorkbench remembers the advisor, and also registers
      // the workbench globally so that all UI plug-ins can find it using
      // PlatformUI.getWorkbench() or AbstractUIPlugin.getWorkbench()
      //int returnCode = PlatformUI.createAndRunWorkbench(display, new TakariWorkbenchAdvisor(processor));
      int returnCode = PlatformUI.createAndRunWorkbench(display, advisor);

      // the workbench doesn't support relaunch yet (bug 61809) so
      // for now restart is used, and exit data properties are checked
      // here to substitute in the relaunch return code if needed
      if (returnCode != PlatformUI.RETURN_RESTART) {
        return EXIT_OK;
      }

      // if the exit code property has been set to the relaunch code, then
      // return that code now, otherwise this is a normal restart
      return EXIT_RELAUNCH.equals(Integer.getInteger(PROP_EXIT_CODE)) ? EXIT_RELAUNCH : EXIT_RESTART;
    } finally {
      if (display != null) {
        display.dispose();
      }
      Location instanceLoc = Platform.getInstanceLocation();
      if (instanceLoc != null) {
        instanceLoc.release();
      }
    }
  }

  /**
   * Creates the display used by the application.
   *
   * @return the display used by the application
   */
  @Override
  protected Display createDisplay() {
    return PlatformUI.createDisplay();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
   */
  @Override
  public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
    // There is nothing to do for IDEApplication
  }

  private static boolean isDevLaunchMode(Map args) {
    // see org.eclipse.pde.internal.core.PluginPathFinder.isDevLaunchMode()
    if (Boolean.getBoolean("eclipse.pde.launch")) {
      return true;
    }
    return args.containsKey("-pdelaunch"); //$NON-NLS-1$
  }

  /**
   * Look at the argument URL for the workspace's version information. Return that version if found and null otherwise.
   */
  protected static Version readWorkspaceVersion(URL workspace) {
    File versionFile = getVersionFile(workspace, false);
    if (versionFile == null || !versionFile.exists()) {
      return null;
    }

    try {
      // Although the version file is not spec'ed to be a Java properties
      // file, it happens to follow the same format currently, so using
      // Properties to read it is convenient.
      Properties props = new Properties();
      FileInputStream is = new FileInputStream(versionFile);
      try {
        props.load(is);
      } finally {
        is.close();
      }

      String versionString = props.getProperty(WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME);
      if (versionString != null) {
        return Version.parseVersion(versionString);
      }
      versionString = props.getProperty(WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME_LEGACY);
      if (versionString != null) {
        return Version.parseVersion(versionString);
      }
      return null;
    } catch (IOException e) {
      IDEWorkbenchPlugin.log("Could not read version file " + versionFile, new Status( //$NON-NLS-1$
          IStatus.ERROR, IDEWorkbenchPlugin.IDE_WORKBENCH, IStatus.ERROR, e.getMessage() == null ? "" : e.getMessage(), //$NON-NLS-1$
          e));
      return null;
    } catch (IllegalArgumentException e) {
      IDEWorkbenchPlugin.log("Could not parse version in " + versionFile, new Status( //$NON-NLS-1$
          IStatus.ERROR, IDEWorkbenchPlugin.IDE_WORKBENCH, IStatus.ERROR, e.getMessage() == null ? "" : e.getMessage(), //$NON-NLS-1$
          e));
      return null;
    }
  }

  /**
   * Write the version of the metadata into a known file overwriting any existing file contents. Writing the version file isn't really crucial, so the function is silent about failure
   */
  private static void writeWorkspaceVersion() {
    if (WORKSPACE_CHECK_REFERENCE_BUNDLE_VERSION == null) {
      // no reference bundle installed, no check possible
      return;
    }

    Location instanceLoc = Platform.getInstanceLocation();
    if (instanceLoc == null || instanceLoc.isReadOnly()) {
      return;
    }

    File versionFile = getVersionFile(instanceLoc.getURL(), true);
    if (versionFile == null) {
      return;
    }

    OutputStream output = null;
    try {
      output = new FileOutputStream(versionFile);
      Properties props = new Properties();

      // write new property
      props.setProperty(WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME, WORKSPACE_CHECK_REFERENCE_BUNDLE_VERSION.toString());

      // write legacy property with an incremented version,
      // so that pre-4.4 IDEs will also warn about the workspace
      props.setProperty(WORKSPACE_CHECK_REFERENCE_BUNDLE_NAME_LEGACY, WORKSPACE_CHECK_LEGACY_VERSION_INCREMENTED);

      props.store(output, null);
    } catch (IOException e) {
      IDEWorkbenchPlugin.log("Could not write version file", //$NON-NLS-1$
          StatusUtil.newStatus(IStatus.ERROR, e.getMessage(), e));
    } finally {
      try {
        if (output != null) {
          output.close();
        }
      } catch (IOException e) {
        // do nothing
      }
    }
  }

  /**
   * The version file is stored in the metadata area of the workspace. This method returns an URL to the file or null if the directory or file does not exist (and the create parameter is false).
   *
   * @param create If the directory and file does not exist this parameter controls whether it will be created.
   * @return An url to the file or null if the version file does not exist or could not be created.
   */
  protected static File getVersionFile(URL workspaceUrl, boolean create) {
    if (workspaceUrl == null) {
      return null;
    }

    try {
      // make sure the directory exists
      File metaDir = new File(workspaceUrl.getPath(), METADATA_FOLDER);
      if (!metaDir.exists() && (!create || !metaDir.mkdir())) {
        return null;
      }

      // make sure the file exists
      File versionFile = new File(metaDir, VERSION_FILENAME);
      if (!versionFile.exists() && (!create || !versionFile.createNewFile())) {
        return null;
      }

      return versionFile;
    } catch (IOException e) {
      // cannot log because instance area has not been set
      return null;
    }
  }

  /**
   * @return the major and minor parts of the given version
   */
  protected static Version toMajorMinorVersion(Version version) {
    return new Version(version.getMajor(), version.getMinor(), 0);
  }

  /*
   * (non-Javadoc)
   *
   * @see org.eclipse.equinox.app.IApplication#stop()
   */
  @Override
  public void stop() {
    final IWorkbench workbench = PlatformUI.getWorkbench();
    if (workbench == null) {
      return;
    }
    final Display display = workbench.getDisplay();
    display.syncExec(new Runnable() {
      @Override
      public void run() {
        if (!display.isDisposed()) {
          workbench.close();
        }
      }
    });
  }
}
