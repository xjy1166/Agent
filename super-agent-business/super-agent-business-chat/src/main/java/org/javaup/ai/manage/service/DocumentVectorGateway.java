package org.javaup.ai.manage.service;

import org.javaup.ai.manage.data.SuperAgentDocumentChunk;

import java.util.List;

/**

 * @description: 服务层

 **/

public interface DocumentVectorGateway {

    void vectorize(List<SuperAgentDocumentChunk> chunkList);

    void deleteByDocumentId(Long documentId);
}
