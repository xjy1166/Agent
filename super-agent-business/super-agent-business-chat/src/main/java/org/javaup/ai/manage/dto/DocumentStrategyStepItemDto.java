package org.javaup.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**

 * @description: 数据传输对象

 **/

@Data
public class DocumentStrategyStepItemDto {

    private Integer stepNo;

    @NotNull(message = "策略类型不能为空")
    private Integer strategyType;
}
