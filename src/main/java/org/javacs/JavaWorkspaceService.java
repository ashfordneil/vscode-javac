package org.javacs;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

class JavaWorkspaceService implements WorkspaceService {
    private final CompletableFuture<LanguageClient> client;
    private final JavaLanguageServer server;
    private final JavaTextDocumentService textDocuments;
    private JavaSettings settings = new JavaSettings();

    JavaWorkspaceService(
            CompletableFuture<LanguageClient> client,
            JavaLanguageServer server,
            JavaTextDocumentService textDocuments) {
        this.client = client;
        this.server = server;
        this.textDocuments = textDocuments;
    }

    JavaSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        LOG.info(params.toString());

        switch (params.getCommand()) {
            case "Java.importClass":
                String fileString = (String) params.getArguments().get(0);
                URI fileUri = URI.create(fileString);
                String packageName = (String) params.getArguments().get(1);
                String className = (String) params.getArguments().get(2);
                FocusedResult compiled =
                        server.configured()
                                .compiler
                                .compileFocused(
                                        fileUri, textDocuments.activeContent(fileUri), 1, 1, false);

                if (compiled.compilationUnit.getSourceFile().toUri().equals(fileUri)) {
                    List<TextEdit> edits =
                            new RefactorFile(compiled.task, compiled.compilationUnit)
                                    .addImport(packageName, className);

                    client.join()
                            .applyEdit(
                                    new ApplyWorkspaceEditParams(
                                            new WorkspaceEdit(
                                                    Collections.singletonMap(fileString, edits))));
                }

                break;
            default:
                LOG.warning("Don't know what to do with " + params.getCommand());
        }

        return CompletableFuture.completedFuture("Done");
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(
            WorkspaceSymbolParams params) {
        List<SymbolInformation> infos =
                server.configured()
                        .index
                        .search(params.getQuery())
                        .limit(server.maxItems)
                        .collect(Collectors.toList());

        return CompletableFuture.completedFuture(infos);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {
        settings = Main.JSON.convertValue(change.getSettings(), JavaSettings.class);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        textDocuments.doLint(textDocuments.openFiles());
    }

    private static final Logger LOG = Logger.getLogger("main");
}
