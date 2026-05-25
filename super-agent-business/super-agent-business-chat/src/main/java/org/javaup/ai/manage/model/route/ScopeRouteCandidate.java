package org.javaup.ai.manage.model.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**

 * @description: 模型对象
 
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScopeRouteCandidate {

    private String scopeCode;

    private String scopeName;

    private BigDecimal score;

    private String reason;
}
