package org.javaup.ai.manage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**

 * @description: 文档检索请求

 **/

@Data
@NoArgsConstructor
public class DocumentRetrieveRequest {

    private String question;

    private String retrievalQuery;

    private Long documentId;

    private Long taskId;

    private List<Long> documentIds;

    private List<Long> taskIds;

    private int topK;

    private DocumentRetrieveFilters filters;

    private List<String> queryContextHints;

    public DocumentRetrieveRequest(String question,
                                   String retrievalQuery,
                                   Long documentId,
                                   Long taskId,
                                   int topK) {
        this(question, retrievalQuery, documentId, taskId, topK, null, List.of());
    }

    public DocumentRetrieveRequest(String question,
                                   String retrievalQuery,
                                   Long documentId,
                                   Long taskId,
                                   int topK,
                                   DocumentRetrieveFilters filters) {
        this(question, retrievalQuery, documentId, taskId, topK, filters, List.of());
    }

    public DocumentRetrieveRequest(String question,
                                   String retrievalQuery,
                                   Long documentId,
                                   Long taskId,
                                   int topK,
                                   DocumentRetrieveFilters filters,
                                   List<String> queryContextHints) {
        this.question = question;
        this.retrievalQuery = retrievalQuery;
        this.documentId = documentId;
        this.taskId = taskId;
        this.documentIds = documentId == null ? List.of() : List.of(documentId);
        this.taskIds = taskId == null ? List.of() : List.of(taskId);
        this.topK = topK;
        this.filters = filters;
        this.queryContextHints = queryContextHints;
    }

    public List<Long> resolvedDocumentIds() {
        return documentIds != null && !documentIds.isEmpty()
            ? documentIds
            : (documentId == null ? List.of() : List.of(documentId));
    }

    public List<Long> resolvedTaskIds() {
        return taskIds != null && !taskIds.isEmpty()
            ? taskIds
            : (taskId == null ? List.of() : List.of(taskId));
    }
}
