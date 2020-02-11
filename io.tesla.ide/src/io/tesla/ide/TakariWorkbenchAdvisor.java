package io.tesla.ide;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizard;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.ide.application.IDEWorkbenchAdvisor;

/**
 * Required as the {@link org.eclipse.ui.internal.ide.application.DelayedEventProcessor} isn't sufficiently flexible to handle our pom.xml needs. All other functionality is delegated to the Eclipse
 * IDE's {@link IDEWorkbenchAdvisor}.
 */
@SuppressWarnings("restriction")
public class TakariWorkbenchAdvisor extends IDEWorkbenchAdvisor {

  /** Pending file-open requests from OS */
  private LinkedList<String> documents = new LinkedList<>();

  public TakariWorkbenchAdvisor() {
    super();
  }

  /**
   * Handle SWT.OpenDocument request to open the provided filepath. Always executed on the SWT display thread. The actual handling is done through the idle loop as it is never called (in practice, at
   * least) until after the workbench has been rendered.
   */
  public Listener getOpenDocumentHandler() {
    return new Listener() {
      @Override
      public void handleEvent(Event event) {
        documents.add(event.text);
      }
    };
  }

  @Override
  public void eventLoopIdle(final Display display) {
    while (!documents.isEmpty()) {
      final String path = documents.removeFirst();
      display.asyncExec(new Runnable() {
        @Override
        public void run() {
          openDocument(display, path);
        }
      });
    }
    super.eventLoopIdle(display);
  }

  protected void openDocument(Display display, String path) {
    File f = new File(path);
    if (f.getName().equals("pom.xml")) {
      openPom(display, f);
    } else {
      openFile(display, f);
    }

  }

  private void openPom(Display display, File f) {
    IWorkbench wb = getWorkbenchConfigurer().getWorkbench();
    IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
    if (window == null) {
      if (wb.getWorkbenchWindowCount() == 0) {
        MessageDialog.openError(null, "Unable to open file", "No windows available");
        return;
      }
      window = wb.getWorkbenchWindows()[0];
    }
    ProjectImportConfiguration config = new ProjectImportConfiguration();
    MavenImportWizard importWizard = new MavenImportWizard(config, Collections.singletonList(f.getParent()));
    WizardDialog dialog = new WizardDialog(window.getShell(), importWizard);
    dialog.open();
  }

  /**
   * Open an external file within the IDE. Unfortunately the {@link DelayedEventProcessor} is not sufficiently flexible to allow calling out to it directly.
   * 
   * @param display the current display
   * @param file the file selected by the user
   */
  private void openFile(Display display, File file) {
    IWorkbench wb = getWorkbenchConfigurer().getWorkbench();
    IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
    if (window == null) {
      if (wb.getWorkbenchWindowCount() == 0) {
        MessageDialog.openError(null, "Unable to open file", "No windows available");
        return;
      }
      window = wb.getWorkbenchWindows()[0];
    }
    try {
      IFileStore fileStore = EFS.getStore(file.toURI());
      IDE.openInternalEditorOnFileStore(window.getActivePage(), fileStore);
    } catch (Exception e) {
      MessageDialog.openError(window.getShell(), "Unable to open file", "An exception occurred: " + e);
    }
  }
}
