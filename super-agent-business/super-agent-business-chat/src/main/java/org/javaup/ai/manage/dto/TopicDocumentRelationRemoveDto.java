package org.javaup.ai.manage.dto;

import lombok.Data;

/**

 * @description: 数据传输对象

 **/
@Data
public class TopicDocumentRelationRemoveDto {

    private String topicCode;

    private String documentId;

    private String operatorId;
}
