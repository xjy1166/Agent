package org.javaup.ai.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**

 * @description: 视图对象

 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadVo {

    private Long documentId;

    private Long taskId;

    private String documentName;

    private Integer parseStatus;

    private Integer strategyStatus;

    private Integer indexStatus;
}
