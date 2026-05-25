package org.javaup.ai.manage.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**

 * @description: 支撑组件

 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStructureNodeCandidate {

    /**
     * 节点编号，唯一标识文档结构中的节点
     */
    private Integer nodeNo;

    /**
     * 节点类型，例如标题、段落、列表项等
     */
    private Integer nodeType;

    /**
     * 父节点编号，指向当前节点的父级节点
     */
    private Integer parentNodeNo;

    /**
     * 前一个兄弟节点编号，指向同一层级的前一个节点
     */
    private Integer prevSiblingNodeNo;

    /**
     * 后一个兄弟节点编号，指向同一层级的后一个节点
     */
    private Integer nextSiblingNodeNo;

    /**
     * 节点深度，表示节点在文档树中的层级（根节点为0或1）
     */
    private Integer depth;

    /**
     * 节点代码，用于程序化识别或分类节点的标识符
     */
    private String nodeCode;

    /**
     * 节点标题，通常是章节或段落的标题文本
     */
    private String title;

    /**
     * 锚点文本，用于链接或定位的文本内容
     */
    private String anchorText;

    /**
     * 规范路径，节点在文档中的完整层级路径表示
     */
    private String canonicalPath;

    /**
     * 章节路径，表示节点所属的章节结构路径
     */
    private String sectionPath;

    /**
     * 节点内容文本，包含该节点的主要文本内容
     */
    private String contentText;

    /**
     * 项目索引，用于标识列表或集合中节点的顺序位置
     */
    private Integer itemIndex;
}
