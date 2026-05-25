package org.javaup.ai.chatagent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**

 * @description: 工具类

 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchRequest {

    private String query;
    private String topic;
    private Integer maxResults;
}
