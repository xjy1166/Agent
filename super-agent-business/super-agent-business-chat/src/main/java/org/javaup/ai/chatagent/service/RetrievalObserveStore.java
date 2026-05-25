package org.javaup.ai.chatagent.service;

import org.javaup.ai.chatagent.model.ChannelExecutionView;
import org.javaup.ai.chatagent.model.RetrievalResultView;

import java.util.List;

/**

 * @description: 服务层
 
 **/

public interface RetrievalObserveStore {

    void batchSaveResults(String conversationId, long exchangeId, List<RetrievalResultView> results);

    void batchSaveChannelExecutions(String conversationId, long exchangeId, List<ChannelExecutionView> executions);

    List<RetrievalResultView> listResults(String conversationId, long exchangeId);

    List<ChannelExecutionView> listChannelExecutions(String conversationId, long exchangeId);

    void deleteByConversation(String conversationId);
}
