package org.javaup.ai.chatagent.dto;

import lombok.Data;

/**

 * @description: 数据传输对象

 **/

@Data
public class ConversationSessionListQueryDto {

    private String keyword;

    private String chatMode;

    private String turnStatus;

    private String pageNo;

    private String pageSize;
}
