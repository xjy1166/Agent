package org.javaup.ai.manage.dto;

import lombok.Data;

/**

 * @description: 数据传输对象

 **/

@Data
public class DocumentPageQueryDto {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;
}
