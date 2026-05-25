package org.javaup.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**

 * @description: 数据传输对象

 **/

@Data
public class DocumentDetailQueryDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;
}
