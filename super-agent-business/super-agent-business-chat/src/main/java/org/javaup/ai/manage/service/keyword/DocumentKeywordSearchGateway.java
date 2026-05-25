package org.javaup.ai.manage.service.keyword;

import org.javaup.ai.manage.data.SuperAgentDocumentChunk;
import org.javaup.ai.manage.model.DocumentRetrieveRequest;
import org.springframework.ai.document.Document;

import java.util.List;

/**

 * @description: 服务层

 **/

public interface DocumentKeywordSearchGateway {

    void indexChunks(List<SuperAgentDocumentChunk> chunkList);

    List<Document> search(DocumentRetrieveRequest request);

    void deleteByDocumentId(Long documentId);
}
