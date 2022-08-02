package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang3.mutable.MutableInt;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.intranda.digiverso.pdf.PDFConverter;
import de.intranda.digiverso.pdf.exception.PDFReadException;
import de.intranda.digiverso.pdf.exception.PDFWriteException;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class FulltextGenerationStepPlugin implements IStepPluginVersion2 {

    // for epub: install calibre or epub2txt2
    // for pdf: install ghostscript

    private static final String DEFAULT_ENCODING = "utf-8";

    @Getter
    private String title = "intranda_step_fulltext_generation";
    @Getter
    private Step step;
    @Getter
    private String value;
    @Getter
    private boolean allowTaskFinishButtons;
    private String returnPath;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        value = myconfig.getString("value", "default value");
        allowTaskFinishButtons = myconfig.getBoolean("allowTaskFinishButtons", false);
        log.info("FulltextGeneration step plugin initialized");
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_fulltext_generation.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {

        Process process = step.getProzess();
        MutableInt counter = new MutableInt(1);
        // list files
        List<Path> originalFiles = null;
        try {
            String imageFolder = process.getImagesTifDirectory(false);
            originalFiles = StorageProvider.getInstance().listFiles(imageFolder);
        } catch (IOException | SwapException e) {
            log.error(e);
            return PluginReturnValue.ERROR;
        }
        if (originalFiles != null && !originalFiles.isEmpty()) {
            Path textFolder = null;
            Path pdfFolder = null;
            Path altoFolder = null;
            Path imagesFolder = null;
            try {
                textFolder = Paths.get(process.getOcrTxtDirectory());
                pdfFolder = Paths.get(process.getOcrPdfDirectory());
                altoFolder = Paths.get(process.getOcrAltoDirectory());
                imagesFolder = Paths.get(process.getThumbsDirectory(), process.getTitel() + "_media");
                if (!StorageProvider.getInstance().isFileExists(textFolder)) {
                    StorageProvider.getInstance().createDirectories(textFolder);
                }
                if (!StorageProvider.getInstance().isFileExists(pdfFolder)) {
                    StorageProvider.getInstance().createDirectories(pdfFolder);
                }
                if (!StorageProvider.getInstance().isFileExists(altoFolder)) {
                    StorageProvider.getInstance().createDirectories(altoFolder);
                }
                if (!StorageProvider.getInstance().isFileExists(imagesFolder)) {
                    StorageProvider.getInstance().createDirectories(imagesFolder);
                }

            } catch (SwapException | IOException e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }

            for (Path source : originalFiles) {
                // check file extension
                // if pdf
                if (source.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                    try {
                        PDFConverter.writeFullText(source.toFile(), textFolder.toFile(), DEFAULT_ENCODING, counter.toInteger());
                        List<File> imageFiles = PDFConverter.writeImages(source.toFile(), imagesFolder.toFile(), counter.toInteger(), 300, "tif",
                                new File(ConfigurationHelper.getInstance().getTemporaryFolder()), "ghostscript");
                        List<File> singlePagePdfs = PDFConverter.writeSinglePagePdfs(source.toFile(), pdfFolder.toFile(), 1);
                        for (int i = 0; i < singlePagePdfs.size(); i++) {
                            File pdfFile = singlePagePdfs.get(i);
                            File imageFile = null;
                            if (i < imageFiles.size()) {
                                imageFile = imageFiles.get(i);
                            }
                            PDFConverter.writeAltoFile(pdfFile, altoFolder.toFile(), imageFile, false);
                        }
                        counter.add(Math.max(singlePagePdfs.size(), imageFiles.size()));
                    } catch (PDFReadException | PDFWriteException e) {
                        log.error(e);
                        return PluginReturnValue.ERROR;
                    }
                }
                // if epub
                else if (source.getFileName().toString().toLowerCase().endsWith(".epub")) {
                    // call cli command from config
                }

            }

        }

        return PluginReturnValue.FINISH;
    }
}