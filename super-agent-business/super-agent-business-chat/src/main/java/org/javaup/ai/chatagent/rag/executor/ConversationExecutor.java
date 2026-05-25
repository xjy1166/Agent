package org.javaup.ai.chatagent.rag.executor;

import org.javaup.ai.chatagent.rag.model.ExecutionMode;
import org.javaup.ai.chatagent.service.TaskInfo;
import reactor.core.publisher.Flux;

/**
 
 * @description: 统一对话执行器抽象

 **/

public interface ConversationExecutor {

    ExecutionMode mode();

    Flux<String> execute(TaskInfo taskInfo);
}
