package org.javaup.ai.chatagent.tool;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.javaup.ai.chatagent.model.SearchReference;

/**
 
 * @description: 工具类

 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchToolResult {

    private String query;
    private String answer;
    private List<SearchReference> results;
}
