package com.xeneta.plugins;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;

import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CompileTemplateAction extends AnAction {
    Logger LOG = Logger.getInstance(CompileTemplateAction.class);
    private Pattern patternIf = Pattern.compile("\\{% if (.*) %}");
    private Pattern patternExpr = Pattern.compile("\\{\\{ (.*?) }}");

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
        List<String> keyword = new ArrayList<>();

        Matcher matcherIf = patternIf.matcher(input);
        while(matcherIf.find()) {
            keyword.add(matcherIf.group(1));
        }

        Matcher matcherExpr = patternExpr.matcher(input);
        while(matcherExpr.find()) {
            keyword.add(matcherExpr.group(1));
        }

        return keyword;
    }

    public void writeToFile(File tempFile, String template, Map<String, String> dataModel) throws IOException, URISyntaxException {
        URI pyFileURI = this.getClass().getResource("/compile.py").toURI();
        String compilePy = Files.readAllLines(Paths.get(pyFileURI), Charset.defaultCharset())
            .stream()
            .collect(Collectors.joining("\n"));

        FileWriter fw = new FileWriter(tempFile);
        fw.write("template = \"\"\"\n" + template + "\n\"\"\"\n\n");
        String modelPy = dataModel.entrySet().stream()
            .map(e -> "'" + e.getKey() + "': '" + e.getValue() + "'")
            .collect(Collectors.joining(", "));

        fw.write("model = {" + modelPy + "}\n\n");
        fw.write(compilePy);
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

    public Map<String, String> showDialog(Project project, List<String> keywords) {
        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("Data model for template render");
        builder.addOkAction().setText("Render");
        builder.addCancelAction().setText("Abort");
        DataModelPanel panel = new DataModelPanel(keywords);
        builder.setCenterPanel(panel);
        builder.setOkActionEnabled(true);

        switch (builder.show()) {
            case DialogWrapper.OK_EXIT_CODE:
                return panel.getData();

            case DialogWrapper.CANCEL_EXIT_CODE:
            default:
                return null;
        }
    }

    /**
     * This takes care of actually parsing the needed values and pops up a window to ask for values.
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        final SelectionModel selectionModel = editor.getSelectionModel();

        String selectedText = selectionModel.getSelectedText();

        if(selectedText == null) {
            LOG.error("No selected text...");
            return;
        }

        List<String> keywords = getKeywords(selectedText);
        Map<String, String> dataModel = showDialog(project, keywords);

        LOG.info("Keywords: " + keywords);

        if(dataModel == null) {
            LOG.info("Data model missing...");
            return;
        }

        try {
            File tempFile = File.createTempFile("template-", ".tmp");

            writeToFile(tempFile, selectedText, dataModel);
            String output = compileTemplate(tempFile);

            //tempFile.delete();

            LOG.info("Selected text: [" + selectedText + "]");
            LOG.info("Output: [" + output + "]");

            CopyPasteManager cpMgr = CopyPasteManager.getInstance();
            cpMgr.setContents(new StringSelection(output));
        }
        catch(InterruptedException | URISyntaxException | IOException ex) {
            LOG.error("Process execution failed", ex);
        }
    }
}
