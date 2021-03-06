// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.MapView.LayerChangeListener;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;

public abstract class FileImporter implements Comparable<FileImporter>, LayerChangeListener {

    /**
     * The extension file filter used to accept files.
     */
    public final ExtensionFileFilter filter;

    private boolean enabled;

    /**
     * Constructs a new {@code FileImporter} with the given extension file filter.
     * @param filter The extension file filter
     */
    public FileImporter(ExtensionFileFilter filter) {
        this.filter = filter;
        this.enabled = true;
    }

    /**
     * Determines if this file importer accepts the given file.
     * @param pathname The file to test
     * @return {@code true} if this file importer accepts the given file, {@code false} otherwise
     */
    public boolean acceptFile(File pathname) {
        return filter.acceptName(pathname.getName());
    }

    /**
     * A batch importer is a file importer that prefers to read multiple files at the same time.
     * @return {@code true} if this importer is a batch importer
     */
    public boolean isBatchImporter() {
        return false;
    }

    /**
     * Needs to be implemented if isBatchImporter() returns false.
     * @param file file to import
     * @param progressMonitor progress monitor
     * @throws IOException if any I/O error occurs
     * @throws IllegalDataException if invalid data is read
     */
    public void importData(File file, ProgressMonitor progressMonitor) throws IOException, IllegalDataException {
        throw new IOException(tr("Could not import ''{0}''.", file.getName()));
    }

    /**
     * Needs to be implemented if isBatchImporter() returns true.
     * @param files files to import
     * @param progressMonitor progress monitor
     * @throws IOException if any I/O error occurs
     * @throws IllegalDataException if invalid data is read
     */
    public void importData(List<File> files, ProgressMonitor progressMonitor) throws IOException, IllegalDataException {
        throw new IOException(tr("Could not import files."));
    }

    /**
     * Wrapper to give meaningful output if things go wrong.
     * @return true if data import was successful
     */
    public boolean importDataHandleExceptions(File f, ProgressMonitor progressMonitor) {
        try {
            Main.info("Open file: " + f.getAbsolutePath() + " (" + f.length() + " bytes)");
            importData(f, progressMonitor);
            return true;
        } catch (IllegalDataException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ImportCancelException) {
                displayCancel(cause);
            } else {
                displayError(f, e);
            }
            return false;
        } catch (Exception e) {
            displayError(f, e);
            return false;
        }
    }

    private static void displayError(File f, Exception e) {
        Main.error(e);
        HelpAwareOptionPane.showMessageDialogInEDT(
                Main.parent,
                tr("<html>Could not read file ''{0}''.<br>Error is:<br>{1}</html>", f.getName(), e.getMessage()),
                tr("Error"),
                JOptionPane.ERROR_MESSAGE, null
        );
    }

    private static void displayCancel(final Throwable t) {
        GuiHelper.runInEDTAndWait(new Runnable() {
            @Override
            public void run() {
                Notification note = new Notification(t.getMessage());
                note.setIcon(JOptionPane.INFORMATION_MESSAGE);
                note.setDuration(Notification.TIME_SHORT);
                note.show();
            }
        });
    }

    public boolean importDataHandleExceptions(List<File> files, ProgressMonitor progressMonitor) {
        try {
            Main.info("Open "+files.size()+" files");
            importData(files, progressMonitor);
            return true;
        } catch (Exception e) {
            Main.error(e);
            HelpAwareOptionPane.showMessageDialogInEDT(
                    Main.parent,
                    tr("<html>Could not read files.<br>Error is:<br>{0}</html>", e.getMessage()),
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE, null
            );
            return false;
        }
    }

    /**
     * If multiple files (with multiple file formats) are selected,
     * they are opened in the order of their priorities.
     * Highest priority comes first.
     */
    public double getPriority() {
        return 0;
    }

    @Override
    public int compareTo(FileImporter other) {
        return Double.compare(this.getPriority(), other.getPriority());
    }

    /**
     * Returns the enabled state of this {@code FileImporter}. When enabled, it is listed and usable in "File-&gt;Open" dialog.
     * @return true if this {@code FileImporter} is enabled
     * @since 5459
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the enabled state of the {@code FileImporter}. When enabled, it is listed and usable in "File-&gt;Open" dialog.
     * @param enabled true to enable this {@code FileImporter}, false to disable it
     * @since 5459
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void activeLayerChange(Layer oldLayer, Layer newLayer) {
        // To be overriden by subclasses if their enabled state depends of the active layer nature
    }

    @Override
    public void layerAdded(Layer newLayer) {
        // To be overriden by subclasses if needed
    }

    @Override
    public void layerRemoved(Layer oldLayer) {
        // To be overriden by subclasses if needed
    }
}
