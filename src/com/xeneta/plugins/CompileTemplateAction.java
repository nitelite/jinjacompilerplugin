package com.xeneta.plugins;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;

import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CompileTemplateAction extends AnAction {

    Logger LOG = Logger.getInstance(CompileTemplateAction.class);

    private String pythonFilePrefix = "Jinja Template Compilation\nLines needed to run jinja...\n\n";
    private Pattern keywordPattern = Pattern.compile("\\{% if (.*) %}");

    /**
     * This method makes sure that the action is only available if you have a project running, a document open and a text selected.
     */
    @Override
    public void update(final AnActionEvent e) {
        //Get required data keys
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        //Set visibility only in case of existing project and editor
        e.getPresentation().setVisible((project != null && editor != null && editor.getSelectionModel().hasSelection()));
    }

    public List<String> getKeywords(String input) {
        Matcher matcher = keywordPattern.matcher(input);
        List<String> keyword = new ArrayList<>();

        while(matcher.find()) {
            keyword.add(matcher.group(0));
        }

        return keyword;
    }

    public void writeToFile(File tempFile, String template) throws IOException {
        FileWriter fw = new FileWriter(tempFile);
        fw.write("template = \"\"\"\n" + template + "\n\"\"\"");
        fw.write(pythonFilePrefix);
        fw.flush();
        fw.close();
    }

    public String compileTemplate(File tempFile) throws IOException, InterruptedException {
        Process python = new ProcessBuilder()
            .command("python", tempFile.getAbsolutePath())
            .inheritIO()
            .start();

        python.waitFor(500, TimeUnit.MILLISECONDS);

        String output;
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(python.getInputStream()))) {
            output = buffer.lines().collect(Collectors.joining("\n"));
        }

        return output;
    }

    /**
     * This takes care of actually parsing the needed values and pops up a window to ask for values.
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        //Get all the required data from data keys
        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        final SelectionModel selectionModel = editor.getSelectionModel();

        String selectedText = selectionModel.getSelectedText();

        if(selectedText == null) {
            LOG.error("No selected text...");
            return;
        }

        List<String> keywords = getKeywords(selectedText);

        LOG.info("Keywords: " + keywords);

        try {
            File tempFile = File.createTempFile("template-", ".tmp");

            writeToFile(tempFile, selectedText);
            String output = compileTemplate(tempFile);

            LOG.info("Selected text: [" + selectedText + "]");
            LOG.info("Output: [" + output + "]");

            CopyPasteManager cpMgr = CopyPasteManager.getInstance();
            cpMgr.setContents(new StringSelection(output));
        }
        catch(InterruptedException ex) {
            LOG.error("Process execution was interrupted", ex);
        }
        catch(IOException ex) {
            LOG.error("Unable to parse template", ex);
        }
    }
}
