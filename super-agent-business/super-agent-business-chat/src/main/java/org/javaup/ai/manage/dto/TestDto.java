package org.javaup.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 
 * @description: 数据传输对象
 
 **/
@Data
public class TestDto {

    @NotNull
    private Long id;
}
