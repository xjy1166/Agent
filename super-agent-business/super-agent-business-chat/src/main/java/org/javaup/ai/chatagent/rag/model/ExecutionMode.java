package org.javaup.ai.chatagent.rag.model;

/**

 * @description: 对话执行模式
 
 **/

public enum ExecutionMode {

    GRAPH_ONLY,

    GRAPH_THEN_EVIDENCE,

    RETRIEVAL,

    REACT_AGENT,

    CLARIFICATION,

    @Deprecated
    RAG_CHAT
}
