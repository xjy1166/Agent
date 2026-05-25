# 超级智能体 - 后端学习文档

> **学习目标**: 4周掌握项目后端核心技术，能独立讲解每个模块的设计思路与实现细节
> **学习方式**: 按阶段推进，每个阶段有明确的学习检查点
> **前置要求**: Java 17+、Spring Boot 基础、Maven 基础

---

## 阶段一：项目骨架与基础设施（第1周）

### 第1章 项目整体架构

#### 1.1 项目定位

超级智能体是一个企业级 RAG（检索增强生成）知识问答系统。核心流程：用户提问 → 查询改写 → 知识检索 → 答案生成 → 流式返回。

#### 1.2 Maven 多模块结构

```
super-agent-origin/                    # 父工程
├── super-agent-common/                # 公共模块（日志、工具、统一响应）
├── super-agent-id-generator/          # 分布式ID生成器（雪花算法）
├── super-agent-redisson/              # Redisson 封装（锁、延迟队列）
├── super-agent-business/              # 核心业务（多模块聚合）
│   ├── super-agent-business-manage/   # 文档管理（上传、解析、索引）
│   └── super-agent-business-chat/     # 智能问答（RAG 检索、对话）
├── super-agent-ai-platform/           # AI平台集成（Embedding、大模型）
├── super-agent-manage/                # 管理后台（用户、权限）
├── super-agent-gateway/               # API 网关
├── super-agent-monitor/               # 监控服务
└── super-agent-ui/                    # 前端（Vue3，本阶段跳过）
```

**父子 POM 关系**: 父 POM 统一管理依赖版本，子模块通过 `<parent>` 继承。

#### 1.3 父 POM 核心配置

```xml
<!-- super-agent-origin/pom.xml -->
<groupId>org.javaup</groupId>
<artifactId>super-agent-origin</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>

<modules>
    <module>super-agent-common</module>
    <module>super-agent-id-generator</module>
    <module>super-agent-redisson</module>
    <module>super-agent-business</module>
    <module>super-agent-ai-platform</module>
    <!-- ... 其他模块 -->
</modules>
```

**关键技术依赖版本管理**（在 `<dependencyManagement>` 中统一声明）:
- Spring Boot 3.x
- Spring AI 1.1.0
- Spring AI Alibaba 1.1.2.0
- MyBatis-Plus 3.5.x
- Hutool 工具库
- Jackson JSON 处理

**学习检查点**:
- [ ] 能画出模块依赖关系图
- [ ] 理解 `dependencyManagement` vs `dependencies` 的区别
- [ ] 知道每个子模块的职责边界

---

### 第2章 公共模块与统一约定

#### 2.1 统一响应格式

```java
// super-agent-common 中定义的统一返回体
public class ApiResponse<T> {
    private int code;       // 业务状态码
    private String message; // 提示信息
    private T data;         // 业务数据

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
```

#### 2.2 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ApiResponse.error("系统内部错误");
    }
}
```

#### 2.3 统一日志规范

通过 `TraceIdFilter` 在每个请求入口生成 TraceId，贯穿整个调用链，便于排查问题。

```java
public class TraceIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

#### 2.4 配置文件约定

每个模块有独立的 `application.yml`，通过 Spring Profile 区分环境：

```yaml
# application-dev.yml
spring:
  profiles:
    active: dev

# 核心中间件配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/super_agent
  ai:
    openai:
      base-url: http://localhost:8080/v1  # DeepSeek API
```

**学习检查点**:
- [ ] 理解 MDC 日志追踪机制
- [ ] 能说出统一响应体的设计好处
- [ ] 知道 Spring Profile 如何区分环境

---

### 第3章 数据库设计

#### 3.1 核心表结构

**文档表 (t_document)**
```sql
CREATE TABLE t_document (
    id            BIGINT PRIMARY KEY,     -- 雪花ID
    doc_name      VARCHAR(255) NOT NULL,  -- 文档名称
    doc_type      VARCHAR(50),            -- 文档类型（pdf/docx/txt/md）
    minio_path    VARCHAR(500),           -- MinIO 存储路径
    parsed_text_path VARCHAR(500),        -- 解析后文本路径
    status        INT DEFAULT 0,          -- 状态（0待解析/1解析中/2已解析/3索引中/4已索引/5失败）
    chunk_strategy VARCHAR(50),           -- 分块策略（STRUCTURE/RECURSIVE/SEMANTIC/LLM）
    quality_level VARCHAR(20),            -- 内容质量（HIGH/MEDIUM/LOW）
    token_count   INT,                    -- 估算token数
    created_time  DATETIME DEFAULT NOW(),
    updated_time  DATETIME DEFAULT NOW() ON UPDATE NOW()
);
```

**文档分块表 (t_document_chunk)**
```sql
CREATE TABLE t_document_chunk (
    id            BIGINT PRIMARY KEY,
    document_id   BIGINT NOT NULL,        -- 关联文档
    chunk_index   INT,                    -- 块序号
    chunk_type    VARCHAR(20),            -- PARENT/CHILD
    content       TEXT,                   -- 块内容
    parent_chunk_id BIGINT,               -- 父块ID（CHILD类型）
    token_count   INT,
    section_path  VARCHAR(500),           -- 章节路径
    created_time  DATETIME DEFAULT NOW()
);
```

**对话表 (t_conversation)**
```sql
CREATE TABLE t_conversation (
    id            BIGINT PRIMARY KEY,
    session_id    VARCHAR(64) NOT NULL,   -- 会话ID
    role          VARCHAR(20),            -- user/assistant
    content       TEXT,                   -- 消息内容
    references_json JSON,                -- 引用来源
    created_time  DATETIME DEFAULT NOW()
);
```

#### 3.2 PGVector 向量表

向量存储在 PostgreSQL + PGVector 插件中：

```sql
CREATE TABLE document_embedding (
    id            VARCHAR(64) PRIMARY KEY,
    document_id   BIGINT NOT NULL,
    chunk_id      BIGINT NOT NULL,
    embedding     vector(1024),           -- 1024维向量（Qwen3 Embedding）
    metadata      JSONB,                  -- 元数据（章节、类型等）
    created_time  TIMESTAMP DEFAULT NOW()
);

-- HNSW 向量索引
CREATE INDEX idx_embedding_hnsw ON document_embedding
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 200);
```

**学习检查点**:
- [ ] 理解为什么向量维度是 1024
- [ ] 知道 HNSW 索引和暴力搜索的区别
- [ ] 能说清 Parent-Child 分块的数据表关系

---

### 第4章 Spring Boot 启动与配置

#### 4.1 启动入口

```java
// super-agent-business-chat 的启动类
@SpringBootApplication
public class SuperAgentChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(SuperAgentChatApplication.class, args);
    }
}
```

#### 4.2 核心配置项

```yaml
# application.yml 关键配置
spring:
  ai:
    # DeepSeek 大模型（对话用）
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
    # Qwen3 Embedding（向量化用）
    embedding:
      qwen3:
        api-key: ${QWEN3_API_KEY}
        base-url: https://api.siliconflow.cn

# 文档管理配置
document:
  chunk:
    max-parent-chars: 2200    # 父块最大字符数
    parent-overlap: 180       # 父块重叠字符数
    max-child-chars: 480      # 子块最大字符数

# RAG 检索配置
rag:
  rerank:
    enabled: true
    model: BAAI/bge-reranker-v2-m3
    top-n: 10
    url: https://api.siliconflow.cn/v1/rerank
  vector:
    similarity-threshold: 0.5  # 向量相似度最低阈值
  rrf:
    k: 60                      # RRF 公式参数
    candidate-top-k: 80        # 候选池大小
    final-top-k: 10            # 最终返回数量
```

#### 4.3 自定义配置绑定

```java
@Data
@ConfigurationProperties(prefix = "rag")
public class ChatRagProperties {
    private RerankProperties rerank = new RerankProperties();
    private VectorProperties vector = new VectorProperties();
    private RrfProperties rrf = new RrfProperties();

    @Data
    public static class RerankProperties {
        private boolean enabled = true;
        private String model = "BAAI/bge-reranker-v2-m3";
        private String url = "https://api.siliconflow.cn/v1/rerank";
        private String apiKey;
        private int topN = 10;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class VectorProperties {
        private double minSimilarity = 0.5;
    }

    @Data
    public static class RrfProperties {
        private int k = 60;
        private int candidateTopK = 80;
        private int finalTopK = 10;
    }
}
```

**学习检查点**:
- [ ] 理解 `@ConfigurationProperties` 的作用
- [ ] 能说出 Spring AI 的自动配置原理
- [ ] 知道环境变量 `${VAR}` 和配置文件的关系

---

## 阶段二：文档处理流水线（第2周）

### 第5章 文档上传与MinIO存储

#### 5.1 上传接口

```java
// DocumentManageController.java
@RestController
@RequestMapping("/api/document")
public class DocumentManageController {

    private final DocumentStorageService storageService;
    private final DocumentStrategyService strategyService;

    @PostMapping("/upload")
    public ApiResponse<Long> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docName") String docName,
            @RequestParam(value = "chunkStrategy", defaultValue = "STRUCTURE") String chunkStrategy) {

        // 1. 参数校验
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        // 2. 存储到 MinIO，返回文档ID
        Long documentId = storageService.store(file, docName, chunkStrategy);

        // 3. 发送 Kafka 消息触发异步解析
        kafkaProducer.sendParseMessage(documentId);

        return ApiResponse.success(documentId);
    }
}
```

#### 5.2 MinIO 存储服务

```java
// MinioDocumentStorageService.java
@Service
public class MinioDocumentStorageService implements DocumentStorageService {

    private final MinioClient minioClient;

    @Override
    public Long store(MultipartFile file, String docName, String chunkStrategy) {
        Long documentId = idGenerator.nextId();

        // 存储路径: originals/{documentId}/{timestamp}-{filename}
        String objectName = "originals/" + documentId + "/"
                + System.currentTimeMillis() + "-" + file.getOriginalFilename();

        // 确保 bucket 存在
        ensureBucketExists();

        // 上传文件
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        // 保存文档元数据到数据库
        Document doc = new Document();
        doc.setId(documentId);
        doc.setDocName(docName);
        doc.setDocType(getFileExtension(file.getOriginalFilename()));
        doc.setMinioPath(objectName);
        doc.setChunkStrategy(chunkStrategy);
        doc.setStatus(DocumentStatus.PENDING_PARSE);
        documentMapper.insert(doc);

        return documentId;
    }

    private void ensureBucketExists() {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }
}
```

**学习检查点**:
- [x] 理解 MinIO 对象存储和文件系统存储的区别

    Q: 为什么用 MinIO 不用本地文件系统？

    ▎ 本地文件系统只能单机访问，多实例部署时文件不共享。MinIO 作为独立存储服务，提供 HTTP
    ▎ API，任何实例都能访问同一份数据。同时 MinIO
    ▎ 内置多副本机制，数据可靠性比本地磁盘高。在我们的项目中，文档上传和后续的解析、向量化可能在不同实例上执行，MinIO
    ▎ 保证了文件的跨实例可达性。

    Q: 对象存储的"对象"是什么？

    ▎ 对象 = 文件内容 + 唯一 Key + 自定义元数据。和文件系统不同，对象存储没有真正的目录层级，originals/1001/合同.pdf
    ▎ 整个就是一个字符串 Key，不涉及多级目录遍历。这让对象存储可以轻松管理数十亿个文件而不会因为目录树过深而变慢。

- [x] 能说出为什么文件上传后不立即解析，而是发 Kafka 消息

    1. 用户体验

    同步：用户点上传 → 转圈10秒 → 才能做别的事
    异步：用户点上传 → 立刻显示"上传成功，正在处理" → 可以做别的事

    2. 削峰

    场景：晚上批量导入 100 个文档

    同步：100个请求同时进来，每个占一个线程10秒
         → Tomcat 默认线程池200个，直接打满
         → 后续请求全部排队等待

    异步：100个请求进来，100ms内全部处理完，100条消息进 Kafka
         → 消费者按自己的节奏，一个个处理
         → 系统始终稳定

    3. 解耦

    同步：上传服务必须知道解析服务的存在，直接调用
         → 两个服务强耦合，解析服务挂了，上传也失败

    异步：上传服务只管发消息，不关心谁消费
         → 解析服务挂了？消息在 Kafka 里等着
         → 解析服务恢复后，继续消费，不丢消息

    4. 可靠性

    同步：上传过程中断电 → 用户不知道文件到底存没存成功
    异步：上传成功后消息进了 Kafka → 即使消费者重启，消息还在
         → 恢复后继续处理，不会丢任务

- [ ] 知道 multipart 文件的分片上传原理

---

### 第6章 Kafka 异步消息驱动

#### 6.1 消息生产者

```java
// DocumentKafkaProducer.java
@Component
public class DocumentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 发送解析任务，documentId 作为 key 保证同一文档的分区有序 */
    public void sendParseMessage(Long documentId) {
        kafkaTemplate.send(parseTopic, String.valueOf(documentId),
                String.valueOf(documentId));
    }

    /** 发送索引任务 */
    public void sendIndexMessage(Long documentId) {
        kafkaTemplate.send(indexTopic, String.valueOf(documentId),
                String.valueOf(documentId));
    }
}
```

#### 6.2 消息消费者

```java
// DocumentParseConsumer.java
@Component
public class DocumentParseConsumer {

    private final DocumentParserService parserService;

    @KafkaListener(topics = "${document.kafka.parse-topic}")
    public void onParseMessage(ConsumerRecord<String, String> record) {
        Long documentId = Long.parseLong(record.value());
        log.info("收到解析任务: documentId={}", documentId);

        try {
            // 更新状态为解析中
            documentMapper.updateStatus(documentId, DocumentStatus.PARSING);

            // 执行解析
            parserService.parse(documentId);

            // 解析完成后，发送索引消息
            kafkaProducer.sendIndexMessage(documentId);
        } catch (Exception e) {
            log.error("解析失败: documentId={}", documentId, e);
            documentMapper.updateStatus(documentId, DocumentStatus.FAILED);
        }
    }
}
```

**关键设计**:
- `documentId` 作为 Kafka 消息的 key，保证同一文档的消息路由到同一分区，维护处理顺序
- 解析和索引分为两个 Topic，解耦处理流程
- 消费者有异常处理，失败时更新状态

**学习检查点**:
- [x] 理解 Kafka 分区有序性的保证机制

    一句话：Kafka 通过key 路由 + 追加写日志 + offset 顺序消费三者配合，保证分区内严格有序。你的项目用 documentId 作为
    key，确保同一文档的所有消息按发送顺序被消费。

- [x] 能说出为什么解析和索引要分成两个 Topic

    ▎ 中间有人工确认环节，两个阶段的失败处理和扩展策略也不同。 拆成两个
    ▎ Topic，让"解析→等人确认→索引"这条链路可以自然地断开和重连。

- [ ] 知道消费者幂等性处理

---

### 第7章 Tika 文档解析

#### 7.1 解析流程（5步）

```java
// TikaDocumentParserService.java
@Service
public class TikaDocumentParserService implements DocumentParserService {

    @Override
    @Transactional
    public void parse(Long documentId) {
        Document doc = documentMapper.selectById(documentId);

        // Step 1: 从 MinIO 读取原始文件，Tika 提取纯文本
        String rawText = extractRawText(doc.getMinioPath());

        // Step 2: 文本清理（去乱码、去多余空白）
        String cleanedText = cleanupText(rawText);

        // Step 3: 解析结构节点（标题层级、段落）
        List<StructureNode> nodes = structureNodes(cleanedText, doc.getDocType());

        // Step 4: 统计信息（token 数、质量评估）
        TextStatistics stats = statistics(cleanedText);

        // Step 5: 保存解析结果
        saveParsedResult(doc, cleanedText, nodes, stats);
    }

    private String extractRawText(String minioPath) {
        // 从 MinIO 下载文件流
        InputStream inputStream = minioClient.getObject(...);

        // Tika 自动检测文件类型并提取文本
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        parser.parse(inputStream, handler, metadata);

        return handler.toString();
    }

    private String cleanupText(String rawText) {
        return rawText
            .replaceAll("\\x00", "")           // 去 null 字符
            .replaceAll("\\s+", " ")           // 合并空白
            .replaceAll("([。！？])\\1+", "$1") // 去重复标点
            .trim();
    }

    private TextStatistics statistics(String text) {
        int chineseCount = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        int englishWords = text.split("\\s+").length;
        // token 估算：英文单词 + 中文字符 + 其他字符/4
        int estimatedTokens = englishWords + chineseCount + (text.length() - chineseCount) / 4;

        // 质量评估：乱码字符比例
        long brokenChars = text.chars().filter(c -> c == 0xFFFD).count();
        double brokenRatio = (double) brokenChars / Math.max(text.length(), 1);
        QualityLevel quality = brokenRatio > 0.02 ? QualityLevel.LOW
                : brokenRatio > 0.005 ? QualityLevel.MEDIUM
                : QualityLevel.HIGH;

        return new TextStatistics(estimatedTokens, quality);
    }
}
```

#### 7.2 结构节点解析

解析文档的标题层级结构，为后续按结构分块提供依据：

```java
private List<StructureNode> structureNodes(String text, String docType) {
    List<StructureNode> nodes = new ArrayList<>();
    Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    Matcher matcher = headingPattern.matcher(text);

    int lastEnd = 0;
    while (matcher.find()) {
        int level = matcher.group(1).length();  // #数量=层级
        String title = matcher.group(2).trim();

        // 提取该标题下的正文内容
        String content = text.substring(lastEnd, matcher.start()).trim();
        if (!content.isEmpty()) {
            nodes.add(new StructureNode(level, title, content));
        }
        lastEnd = matcher.end();
    }
    return nodes;
}
```

#### 7.3同步导航索引到 Elasticsearch

  private void syncNavigationArtifacts(Long documentId,
                                       Long parseTaskId,
                                       List<SuperAgentDocumentStructureNode> structureNodes) {
      // 1. 同步导航索引到 Elasticsearch
      DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
      if (navigationIndexService != null) {
          navigationIndexService.reindexDocumentNodes(documentId, parseTaskId, structureNodes);
      }

      // 2. 同步结构图投影到 Neo4j
      DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
      if (graphProjectionService != null && graphProjectionService.enabled()) {
          graphProjectionService.projectToGraph(documentId, parseTaskId);
      }
  }

  这个方法做了什么 

  解析阶段提取出结构节点后，需要把这份结构信息同步到两个地方，供后续检索使用：

  同步一：Elasticsearch 导航索引

一句话：导航索引存的是文档的结构节点信息（章节标题、路径、层级关系），专门用于快速定位用户问的是哪个章节。

  结构节点列表
    ↓ reindexDocumentNodes()
  Elasticsearch 索引（供 DocumentQuestionRouter 搜索章节位置）

  用途：用户问"第3章讲了什么"时，路由器通过 ES 搜索章节节点：

// 应该匹配：标题、路径、锚点、正文 ，权重依次降低

  // DocumentQuestionRouter.java 中的 resolveByNavigationIndex
  List<NavigationSectionHit> hits = navigationIndexService.searchSections(
      documentId, query, facet, "", query, 5
  );

  如果没有这一步同步，路由器就搜不到章节位置，_ONLY 和 GRAPH_THEN_EVIDENCE 模式会退化。

  同步二：Neo4j 结构图投影

  结构节点列表（树形父子关系）
    ↓ projectToGraph()
  Neo4j 图数据库（章节节点 + 父子边 + 兄弟边）

  用途：支持图查询，比如"2.1 的上一节是什么"、"这个文档有哪些章节"这类问题。

  为什么要单独同步而不是直接用 MySQL

  结构节点已经存在 MySQL 的 super_agent_document_structure_node 表里了，但：

  ┌─────────────────────────┬───────────────────┬────────────────────────┐
  │        查询场景         │       MySQL       │       更好的选择       │
  ├─────────────────────────┼───────────────────┼────────────────────────┤
  │ 按关键词搜章节          │ LIKE 全表扫描，慢 │ ES 倒排索引，毫秒级    │
  ├─────────────────────────┼───────────────────┼────────────────────────┤
  │ 章节间的上下级/兄弟关系 │ 需要多次 JOIN     │ Neo4j 图遍历，一次查询 │
  ├─────────────────────────┼───────────────────┼────────────────────────┤
  │ 文本相似度匹配章节      │ MySQL 做不了      │ ES 支持 BM25 相关性    │
  └─────────────────────────┴───────────────────┴────────────────────────┘

  所以 syncNavigationArtifacts 的本质是：把 MySQL 里的结构节点数据，按不同查询场景，分发到最适合的存储引擎中。

#### 7.4 AI 推荐切块策略

  作用：根据文档特征（文件类型、结构复杂度、内容质量、长度），自动推荐最优的分块策略组合。

  不调用大模型，纯规则判断。

---
  核心逻辑

  public DocumentStrategyPlanDraft recommendStrategy(Document document,
                                                       DocumentAnalysisResult analysisResult) {

      // ① 判断4种策略是否适用
      boolean structureRecommended = shouldUseStructure(fileType, analysisResult);
      boolean recursiveRecommended = shouldUseRecursive(analysisResult);
      boolean semanticRecommended = shouldUseSemantic(analysisResult);
      boolean llmRecommended = shouldUseLlm(analysisResult);
    
      // ② 组装 Parent 流水线
      List<Integer> parentStrategyTypes = new ArrayList<>();
      if (structureRecommended) {
          parentStrategyTypes = [STRUCTURE];        // 有标题结构 → 结构分块优先
      } else {
          parentStrategyTypes = [RECURSIVE];        // 没结构 → 递归兜底
      }
    
      // ③ 组装 Child 流水线
      List<Integer> childStrategyTypes = new ArrayList<>();
      if (llmRecommended) {
          childStrategyTypes = [LLM, RECURSIVE];         // 低质量 → LLM增强 + 递归兜底
      } else if (semanticRecommended) {
          childStrategyTypes = [SEMANTIC, RECURSIVE];    // 正常 → 语义 + 递归兜底
      } else {
          childStrategyTypes = [RECURSIVE];              // 短文档 → 纯递归
      }
    
      // ④ 返回推荐结果
      return new DocumentStrategyPlanDraft(strategySnapshot, reason, parentSteps, childSteps);
  }

---
  4 个判断条件

  // 条件1：是否用结构分块
  boolean shouldUseStructure(DocumentFileType fileType, AnalysisResult analysis) {
      // 文件类型必须是 PDF/DOCX/MD/HTML
      boolean suitableType = (PDF || DOCX || MD || HTML);
      // 且 结构等级 >= MEDIUM 或 标题数 >= 2
      return suitableType && (analysis.structureLevel >= MEDIUM || analysis.headingCount >= 2);
  }

  // 条件2：是否用递归分块
  boolean shouldUseRecursive(AnalysisResult analysis) {
      // 文档总字数超长 或 某个段落超长
      return analysis.charCount >= 800 || analysis.maxParagraphLength >= 800;
  }

  // 条件3：是否用语义分块
  boolean shouldUseSemantic(AnalysisResult analysis) {
      // 文档够长 + 段落数够多 + 内容质量中等以上
      return analysis.charCount >= 300
          && analysis.paragraphCount >= 3
          && analysis.contentQualityLevel >= MEDIUM;
  }

  // 条件4：是否用LLM分块
  boolean shouldUseLlm(AnalysisResult analysis) {
      // 开启了LLM推荐 + 文档质量差（乱码多）
      return properties.recommendLlmWhenLowQuality
          && analysis.contentQualityLevel == LOW
          && analysis.charCount >= 300;
  }

---
  推荐结果示例

  场景1：正常技术文档（PDF，有标题，质量高）

  shouldUseStructure = true   (PDF + 有标题)
  shouldUseRecursive = true   (10000字超长)
  shouldUseSemantic  = true   (长 + 段落多 + 质量高)
  shouldUseLlm       = false  (质量不低)

  推荐结果：
    Parent: [STRUCTURE]           ← 按标题切，保留章节完整性
    Child:  [SEMANTIC, RECURSIVE] ← 语义切分 + 递归兜底控制长度

  Snapshot: "PARENT:1;CHILD:3,2"
  Reason: "父块流水线优先采用基于文档结构切块；子块流水线优先采用语义分块；子块流水线追加递归分块"

  场景2：纯文本长文（无标题）

  shouldUseStructure = false  (txt类型，无标题)
  shouldUseRecursive = true   (5000字超长)
  shouldUseSemantic  = true   (长 + 段落多)
  shouldUseLlm       = false

  推荐结果：
    Parent: [RECURSIVE]           ← 按2200字递归切
    Child:  [SEMANTIC, RECURSIVE] ← 语义 + 递归

  场景3：OCR扫描件（乱码多，质量低）

  shouldUseStructure = false  (结构识别不稳定)
  shouldUseRecursive = true
  shouldUseSemantic  = false  (质量太低，语义分块不可靠)
  shouldUseLlm       = true   (质量低 → 用LLM兜底)

  推荐结果：
    Parent: [RECURSIVE]           ← 递归切分
    Child:  [LLM, RECURSIVE]      ← LLM智能切 + 递归兜底

  场景4：短文档（200字）

  shouldUseStructure = false
  shouldUseRecursive = false  (不到800字)
  shouldUseSemantic  = false  (不到300字)
  shouldUseLlm       = false

  推荐结果：
    Parent: [RECURSIVE]
    Child:  [RECURSIVE]           ← 纯递归，不切也行（本身够小）

---
  返回的数据结构

  DocumentStrategyPlanDraft {
      strategySnapshot: "PARENT:1;CHILD:3,2",   // 策略快照（可序列化存储）
      recommendReason: "父块流水线优先采用基于文档结构切块...",
      parentSteps: [
          DocumentStrategyStepDraft {
              pipelineType: "PARENT",
              strategyType: 1,         // STRUCTURE
              strategyRole: 1,         // PRIMARY（主策略）
              sourceType: 1,           // SYSTEM_RECOMMEND
              recommendReason: "检测到文档具有较明显的标题或章节结构..."
          }
      ],
      childSteps: [
          DocumentStrategyStepDraft {
              pipelineType: "CHILD",
              strategyType: 3,         // SEMANTIC
              strategyRole: 1,         // PRIMARY
              recommendReason: "文本主题边界相对明确，子块先使用语义分块..."
          },
          DocumentStrategyStepDraft {
              pipelineType: "CHILD",
              strategyType: 2,         // RECURSIVE
              strategyRole: 2,         // FALLBACK（兜底）
              recommendReason: "文档整体较长，追加递归分块控制长度..."
          }
      ]
  }

---
  一句话总结

  ▎ recommendStrategy 是一个规则引擎，根据文档的5个特征（类型、结构、长度、段落数、质量）自动选择 Parent 和 Child
  ▎ 的分块策略链。推荐结果会展示给用户确认，用户可以修改后才真正执行分块。



**学习检查点**:

- [x] 知道 Apache Tika 支持哪些文件格式

- [ ] 理解 token 估算的原理和误差来源

- [ ] 能说清结构节点解析如何为分块策略服务

    一、先看全链路数据流

    原始文档
      ↓
    Tika 解析 → 纯文本
      ↓
    DocumentStructureNodeExtractor → 结构节点列表（存入 super_agent_document_structure_node 表）
      ↓
    DocumentStrategyServiceImpl → 分块时读取结构节点 → 生成 Parent-Child 块

    结构节点不是分完块才有的，是在分块之前就提取好了，分块时直接用。

---
    二、结构节点长什么样
    
    // SuperAgentDocumentStructureNode.java - 每个节点的字段
    {
        id: 1001,
        nodeType: SECTION,          // 节点类型：章节/步骤/列表项
        parentNodeId: null,         // 父节点ID（根节点为null）
        depth: 1,                   // 层级深度
        nodeCode: "1",              // 章节编号
        title: "项目概述",           // 标题
        sectionPath: "项目概述",     // 完整路径
        canonicalPath: "/1-项目概述", // 规范路径
        contentText: "本项目是..."   // 该章节下的正文内容
    }
    
    实际存储是一棵树：
    
      文档："Spring Boot 入门指南"
      │
      ├── [节点1] 1. 项目概述          contentText="Spring Boot 是..."(800字)
      │   ├── [节点2] 1.1 背景         contentText="随着微服务..."(300字)
      │   └── [节点3] 1.2 目标         contentText="本项目旨在..."(500字)
      │
      ├── [节点4] 2. 技术架构          contentText="系统采用..."(1800字)
      │   ├── [节点5] 2.1 整体设计     contentText="架构分为..."(600字)
      │   └── [节点6] 2.2 核心模块     contentText="核心模块包括..."(1200字)
      │
      └── [节点7] 3. 部署说明          contentText="部署分为..."(2400字)
          ├── [节点8] 3.1 环境准备     contentText="需要准备..."(800字)
          └── [节点9] 3.2 安装步骤     contentText="安装步骤如下..."(1600字)

---
    三、分块时怎么用这些节点
    
    核心入口是 buildParentBlocks()：
    
    // DocumentStrategyServiceImpl.java:184
    public List<ParentBlockCandidate> buildParentBlocks(...) {
        // 1. 从数据库读取结构节点
        List<SuperAgentDocumentStructureNode> structureNodes =
            structureNodeService.listDocumentNodes(documentId, taskId);
    
        // 2. 用结构节点构建父块种子列表
        List<ChunkCandidate> parentSeedList =
            buildParentSeedList(parsedText, parentSteps, structureNodes);
      
        // 3. 对每个父块，构建子块种子列表
        for (ChunkCandidate parentSeed : parentSeedList) {
            List<ChunkCandidate> childSeedList =
                buildChildSeedList(parentSeed, childSteps, structureNodes);
        }
    }
    
    父块构建：结构节点 → 父块
    
    private List<ChunkCandidate> buildParentSeedList(...,
            List<SuperAgentDocumentStructureNode> structureNodes) {
    
        // 如果策略里包含 STRUCTURE 策略，且有结构节点
        if (containsStructureStep(parentSteps) && !structureNodes.isEmpty()) {
            // 直接用结构节点作为父块种子
            List<ChunkCandidate> structureSeeds = buildStructureParentSeeds(structureNodes);
      
            // 后续策略（递归/语义/LLM）在结构种子基础上进一步加工
            return executePipeline(structureSeeds, remainingSteps, ...);
        }
      
        // 没有结构节点，就用原始文本走递归分块
        return executePipeline(List.of(全文文本), parentSteps, ...);
    }
    
    private List<ChunkCandidate> buildStructureParentSeeds(
            List<SuperAgentDocumentStructureNode> structureNodes) {
    
        List<ChunkCandidate> seeds = new ArrayList<>();
        for (SuperAgentDocumentStructureNode node : structureNodes) {
            // 只取 SECTION 类型的节点
            if (!SECTION.equals(node.getNodeType())) continue;
      
            // 只取"有内容的叶子章节"（或有实质内容的中间章节）
            if (!isContentBearingSection(node, hasChildSection)) continue;
      
            // 结构节点 → ChunkCandidate
            seeds.add(new ChunkCandidate(
                node.getSectionPath(),      // "项目概述 > 1.1 背景"
                node.getId(),               // structureNodeId
                node.getNodeType(),         // SECTION
                node.getCanonicalPath(),    // "/1-项目概述/1.1-背景"
                node.getItemIndex(),
                node.getContentText(),      // 该章节的正文内容
                ORIGINAL
            ));
        }
        return seeds;
    }
    
    一个结构节点 = 一个天然的父块。不需要硬切，标题边界就是最好的切分点。
    
    子块构建：父块内的子结构 → 子块
    
    private List<ChunkCandidate> buildChildSeedList(ChunkCandidate parentSeed,
            ..., List<SuperAgentDocumentStructureNode> structureNodes) {
    
        // 找到父节点的所有子节点
        for (SuperAgentDocumentStructureNode child : childrenByParent.get(parentSeed.structureNodeId)) {
      
            // 子节点类型是 章节/步骤/列表项 → 作为子块种子
            if (child.nodeType == SECTION || STEP || LIST_ITEM) {
                seeds.add(toChunkCandidate(child));
            }
        }
      
        // 没有子结构 → 用递归/语义策略在父块文本内切分
        if (seeds.isEmpty()) {
            return executePipeline(父块文本, childSteps, ...);
        }
        return seeds;
    }

---
    四、画一张完整对比图
    
    没有结构节点的情况（纯递归切分）
    
    原始文本（5000字）
      ↓ 固定 2200 字切分
    父块1 [0-2200]    父块2 [2020-4220]    父块3 [4040-5000]
      ↓ 固定 480 字切分
    子块1 [0-480]    子块2 [400-880]    ...
    
    问题：可能在句子中间切开，破坏语义完整性
    
    有结构节点的情况（结构感知切分）
    
    结构节点树：
      1. 项目概述 (contentText: 800字)        → 父块1
      │   1.1 背景 (contentText: 300字)       → 子块1-1
      │   1.2 目标 (contentText: 500字)       → 子块1-2
      │
      2. 技术架构 (contentText: 1800字)       → 父块2
      │   2.1 整体设计 (contentText: 600字)   → 子块2-1
      │   2.2 核心模块 (contentText: 1200字)  → 子块2-2
      │
      3. 部署说明 (contentText: 2400字)       → 父块3 (超2200字，递归再切)
          3.1 环境准备 (contentText: 800字)   → 子块3-1
          3.2 安装步骤 (contentText: 1600字)  → 子块3-2 → 递归切为3-2-a, 3-2-b
    
    优势：父块边界 = 标题边界，语义天然完整

---

### 第8章 文档分块策略

#### 8.1 策略入口

```java
// DocumentStrategyServiceImpl.java
@Service
public class DocumentStrategyServiceImpl implements DocumentStrategyService {

    @Override
    public ChunkResult executePipeline(Document doc, String parsedText) {
        ChunkStrategy strategy = ChunkStrategy.valueOf(doc.getChunkStrategy());

        // Step 1: 按策略分块（Parent 层）
        List<Chunk> parentChunks = switch (strategy) {
            case STRUCTURE  -> structureSplit(parsedText);
            case RECURSIVE  -> recursiveSplit(parsedText);
            case SEMANTIC   -> semanticSplit(parsedText);
            case LLM        -> llmSplit(parsedText);
        };

        // Step 2: 为每个 Parent 生成 Child 块
        List<Chunk> childChunks = generateChildChunks(parentChunks);

        return new ChunkResult(parentChunks, childChunks);
    }
}
```

#### 8.2 策略一：按结构分块（STRUCTURE）

按文档标题层级切分，每个标题对应一个 Parent 块：

```java
private List<Chunk> structureSplit(String text) {
    // 正则匹配 Markdown 标题
    Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    Matcher matcher = headingPattern.matcher(text);

    List<Chunk> chunks = new ArrayList<>();
    int lastPos = 0;
    String currentSection = "文档开头";

    while (matcher.find()) {
        String content = text.substring(lastPos, matcher.start()).trim();
        if (content.length() > 50) {  // 太短的不单独成块
            chunks.add(Chunk.builder()
                    .content(content)
                    .sectionPath(currentSection)
                    .build());
        }
        currentSection = matcher.group(2).trim();
        lastPos = matcher.end();
    }
    // 最后一段
    String lastContent = text.substring(lastPos).trim();
    if (lastContent.length() > 50) {
        chunks.add(Chunk.builder().content(lastContent).sectionPath(currentSection).build());
    }
    return chunks;
}
```

#### 8.3 策略二：递归分块（RECURSIVE）

4 级分隔符逐步切分，保证块大小均匀：

```java
private List<Chunk> recursiveSplit(String text) {
    int maxChars = 2200;  // Parent 最大字符数
    int overlap = 180;    // 重叠字符数

    // 4 级分隔符，优先级递减
    String[] separators = {"\n\n", "\n", "。", ""};  // "" = 固定窗口兜底

    List<Chunk> chunks = new ArrayList<>();
    splitRecursive(text, separators, 0, maxChars, overlap, chunks);
    return chunks;
}

private void splitRecursive(String text, String[] separators, int sepIndex,
                            int maxChars, int overlap, List<Chunk> result) {
    if (text.length() <= maxChars) {
        result.add(Chunk.builder().content(text).build());
        return;
    }

    String sep = separators[sepIndex];
    String[] parts;

    if (sep.isEmpty()) {
        // 最后一级：固定窗口切分
        parts = splitByFixedSize(text, maxChars - overlap);
    } else {
        parts = text.split(Pattern.quote(sep), -1);
    }

    StringBuilder buffer = new StringBuilder();
    for (String part : parts) {
        if (buffer.length() + part.length() + sep.length() > maxChars) {
            if (buffer.length() > 0) {
                // 递归：如果当前块仍过大，用下一级分隔符
                splitRecursive(buffer.toString(), separators, sepIndex + 1,
                        maxChars, overlap, result);
                buffer = new StringBuilder();
            }
        }
        if (buffer.length() > 0) buffer.append(sep);
        buffer.append(part);
    }
    if (buffer.length() > 0) {
        splitRecursive(buffer.toString(), separators, sepIndex + 1,
                maxChars, overlap, result);
    }
}
```

#### 8.4 策略三：语义分块（SEMANTIC）

基于相邻句子的 Jaccard 相似度判断语义边界：

```java
private List<Chunk> semanticSplit(String text) {
    double threshold = 0.35;  // 相似度阈值
    int maxChars = 2200;

    String[] sentences = text.split("(?<=[。！？.!?])\\s*");
    List<Chunk> chunks = new ArrayList<>();
    StringBuilder currentChunk = new StringBuilder(sentences[0]);

    for (int i = 1; i < sentences.length; i++) {
        double similarity = jaccardSimilarity(
                currentChunk.toString(), sentences[i]);

        // 相似度低于阈值 或 超过最大长度 → 切分
        if (similarity < threshold || currentChunk.length() > maxChars) {
            chunks.add(Chunk.builder().content(currentChunk.toString()).build());
            currentChunk = new StringBuilder(sentences[i]);
        } else {
            currentChunk.append(sentences[i]);
        }
    }
    if (currentChunk.length() > 0) {
        chunks.add(Chunk.builder().content(currentChunk.toString()).build());
    }
    return chunks;
}

/** Jaccard 相似度：交集大小 / 并集大小 */
private double jaccardSimilarity(String a, String b) {
    Set<String> setA = Set.of(a.split("\\s+"));
    Set<String> setB = Set.of(b.split("\\s+"));
    long intersection = setA.stream().filter(setB::contains).count();
    long union = setA.size() + setB.size() - intersection;
    return union == 0 ? 0 : (double) intersection / union;
}
```

#### 8.5 策略四：LLM 分块

让大模型判断语义切分点，失败时回退到语义分块：

```java
private List<Chunk> llmSplit(String text) {
    try {
        String prompt = "请将以下文本按语义分为多个段落，每段不超过2200字。\n"
                + "返回JSON数组格式：[\"段落1\", \"段落2\", ...]\n\n" + text;

        String response = chatModel.call(prompt);
        List<String> parts = objectMapper.readValue(
                extractJsonArray(response), new TypeReference<>() {});

        return parts.stream()
                .map(p -> Chunk.builder().content(p).build())
                .toList();
    } catch (Exception e) {
        log.warn("LLM 分块失败，回退到语义分块: {}", e.getMessage());
        return semanticSplit(text);  // 降级策略
    }
}
```

#### 8.6 Parent-Child 双层分块

```java
private List<Chunk> generateChildChunks(List<Chunk> parentChunks) {
    int maxChildChars = 480;   // 子块最大字符数
    int childOverlap = 60;     // 子块重叠

    List<Chunk> childChunks = new ArrayList<>();
    for (Chunk parent : parentChunks) {
        if (parent.getContent().length() <= maxChildChars) {
            // 父块本身就够小，直接作为子块
            parent.setChunkType("PARENT");
            childChunks.add(parent.toChildChunk());
        } else {
            // 固定窗口切分子块
            List<Chunk> children = fixedWindowSplit(
                    parent.getContent(), maxChildChars, childOverlap);
            for (Chunk child : children) {
                child.setParentChunkId(parent.getId());
                child.setChunkType("CHILD");
            }
            childChunks.addAll(children);
        }
    }
    return childChunks;
}
```

**Parent-Child 设计理念**:
- **Child 块**（小）: 用于向量检索，精确匹配问题语义
- **Parent 块**（大）: 检索到 Child 后，提升到 Parent 获取完整上下文
- 这样既保证检索精度，又避免答案被切碎

**学习检查点**:

- [ ] 能对比 4 种分块策略的适用场景

  #### ● 结构节点解析如何为分块策略服务

    一、先看全链路数据流

    原始文档
      ↓
    Tika 解析 → 纯文本
      ↓
    DocumentStructureNodeExtractor → 结构节点列表（存入 super_agent_document_structure_node 表）
      ↓
    DocumentStrategyServiceImpl → 分块时读取结构节点 → 生成 Parent-Child 块

    结构节点不是分完块才有的，是在分块之前就提取好了，分块时直接用。

  ---
    二、结构节点长什么样

    // SuperAgentDocumentStructureNode.java - 每个节点的字段
    {
        id: 1001,
        nodeType: SECTION,          // 节点类型：章节/步骤/列表项
        parentNodeId: null,         // 父节点ID（根节点为null）
        depth: 1,                   // 层级深度
        nodeCode: "1",              // 章节编号
        title: "项目概述",           // 标题
        sectionPath: "项目概述",     // 完整路径
        canonicalPath: "/1-项目概述", // 规范路径
        contentText: "本项目是..."   // 该章节下的正文内容
    }

    实际存储是一棵树：

    文档（根）
    ├── 1. 项目概述              ← depth=1, contentText="本项目是..."
    │   ├── 1.1 背景             ← depth=2, contentText="随着..."
    │   └── 1.2 目标             ← depth=2, contentText="本项目旨在..."
    ├── 2. 技术架构              ← depth=1, contentText="系统采用..."
    │   ├── 2.1 整体设计         ← depth=2
    │   │   ├── 步骤一：数据采集  ← depth=3, nodeType=STEP
    │   │   └── 步骤二：数据处理  ← depth=3, nodeType=STEP
    │   └── 2.2 核心模块         ← depth=2
    └── 3. 部署说明              ← depth=1

  ---
    三、分块时怎么用这些节点

    核心入口是 buildParentBlocks()：

    // DocumentStrategyServiceImpl.java:184
    public List<ParentBlockCandidate> buildParentBlocks(...) {
        // 1. 从数据库读取结构节点
        List<SuperAgentDocumentStructureNode> structureNodes =
            structureNodeService.listDocumentNodes(documentId, taskId);

        // 2. 用结构节点构建父块种子列表
        List<ChunkCandidate> parentSeedList =
            buildParentSeedList(parsedText, parentSteps, structureNodes);
      
        // 3. 对每个父块，构建子块种子列表
        for (ChunkCandidate parentSeed : parentSeedList) {
            List<ChunkCandidate> childSeedList =
                buildChildSeedList(parentSeed, childSteps, structureNodes);
        }
    }

    父块构建：结构节点 → 父块

    private List<ChunkCandidate> buildParentSeedList(...,
            List<SuperAgentDocumentStructureNode> structureNodes) {

        // 如果策略里包含 STRUCTURE 策略，且有结构节点
        if (containsStructureStep(parentSteps) && !structureNodes.isEmpty()) {
            // 直接用结构节点作为父块种子
            List<ChunkCandidate> structureSeeds = buildStructureParentSeeds(structureNodes);
      
            // 后续策略（递归/语义/LLM）在结构种子基础上进一步加工
            return executePipeline(structureSeeds, remainingSteps, ...);
        }
      
        // 没有结构节点，就用原始文本走递归分块
        return executePipeline(List.of(全文文本), parentSteps, ...);
    }

    private List<ChunkCandidate> buildStructureParentSeeds(
            List<SuperAgentDocumentStructureNode> structureNodes) {

        List<ChunkCandidate> seeds = new ArrayList<>();
        for (SuperAgentDocumentStructureNode node : structureNodes) {
            // 只取 SECTION 类型的节点
            if (!SECTION.equals(node.getNodeType())) continue;
      
            // 只取"有内容的叶子章节"（或有实质内容的中间章节）
            if (!isContentBearingSection(node, hasChildSection)) continue;
      
            // 结构节点 → ChunkCandidate
            seeds.add(new ChunkCandidate(
                node.getSectionPath(),      // "项目概述 > 1.1 背景"
                node.getId(),               // structureNodeId
                node.getNodeType(),         // SECTION
                node.getCanonicalPath(),    // "/1-项目概述/1.1-背景"
                node.getItemIndex(),
                node.getContentText(),      // 该章节的正文内容
                ORIGINAL
            ));
        }
        return seeds;
    }

    一个结构节点 = 一个天然的父块。不需要硬切，标题边界就是最好的切分点。

    子块构建：父块内的子结构 → 子块

    private List<ChunkCandidate> buildChildSeedList(ChunkCandidate parentSeed,
            ..., List<SuperAgentDocumentStructureNode> structureNodes) {

        // 找到父节点的所有子节点
        for (SuperAgentDocumentStructureNode child : childrenByParent.get(parentSeed.structureNodeId)) {
      
            // 子节点类型是 章节/步骤/列表项 → 作为子块种子
            if (child.nodeType == SECTION || STEP || LIST_ITEM) {
                seeds.add(toChunkCandidate(child));
            }
        }
      
        // 没有子结构 → 用递归/语义策略在父块文本内切分
        if (seeds.isEmpty()) {
            return executePipeline(父块文本, childSteps, ...);
        }
        return seeds;
    }

  ---
    四、画一张完整对比图

    没有结构节点的情况（纯递归切分）

    原始文本（5000字）
      ↓ 固定 2200 字切分
    父块1 [0-2200]    父块2 [2020-4220]    父块3 [4040-5000]
      ↓ 固定 480 字切分
    子块1 [0-480]    子块2 [400-880]    ...

    问题：可能在句子中间切开，破坏语义完整性

    有结构节点的情况（结构感知切分）

  ---
    五、为什么结构感知切分更好
  
    ┌────────────┬────────────────────────┬─────────────────────────────────┐
    │    对比    │       纯递归切分       │          结构感知切分           │
    ├────────────┼────────────────────────┼─────────────────────────────────┤
    │ 切分依据   │ 字符数（2200字一刀切） │ 标题层级（按章节切）            │
      1. 项目概述 (contentText: 800字)        → 父块1
      │   1.1 背景 (contentText: 300字)       → 子块1-1
      │   1.2 目标 (contentText: 500字)       → 子块1-2
      │
      2. 技术架构 (contentText: 1800字)       → 父块2
      │   2.1 整体设计 (contentText: 600字)   → 子块2-1
      │   2.2 核心模块 (contentText: 1200字)  → 子块2-2
      │
      3. 部署说明 (contentText: 2400字)       → 父块3 (超2200字，递归再切)
          3.1 环境准备 (contentText: 800字)   → 子块3-1
          3.2 安装步骤 (contentText: 1600字)  → 子块3-2 → 递归切为3-2-a, 3-2-b
  
    优势：父块边界 = 标题边界，语义天然完整
  
  ---
    五、为什么结构感知切分更好
  
    ┌────────────┬────────────────────────┬─────────────────────────────────┐
    │    对比    │       纯递归切分       │          结构感知切分           │
    ├────────────┼────────────────────────┼─────────────────────────────────┤
    │ 切分依据   │ 字符数（2200字一刀切） │ 标题层级（按章节切）            │
    ├────────────┼────────────────────────┼─────────────────────────────────┤
    │ 边界位置   │ 可能在句子中间         │ 一定在标题处，语义完整          │
    ├────────────┼────────────────────────┼─────────────────────────────────┤
    │ 元数据     │ 只有文本               │ 有 sectionPath、canonicalPath   │
    ├────────────┼────────────────────────┼─────────────────────────────────┤
    │ 检索后定位 │ 只知道"匹配到某段文本" │ 知道"匹配到第2章第1节"          │
    ├────────────┼────────────────────────┼─────────────────────────────────┤
    │ 回答时引用 │ "根据文档内容..."      │ "根据第2章第1节《整体设计》..." │
    └────────────┴────────────────────────┴─────────────────────────────────┘

  ---
    六、一句话总结

    ▎ 结构节点把文档从一坨纯文本变成了一棵有标题层级的树。分块时直接用树的节点作为切分边界（而不是盲目按字符数切），保证每
    ▎ 个块语义完整、边界清晰、可追溯来源。
  
  
  
  #### ❯ 文档分块策略是怎么实现的
  
  ● 文档分块策略实现详解
  
    一、整体架构：双流水线
  
    项目采用 Parent-Child 双层分块，分两条流水线并行推进：
  
    原始文本
      ├─→ Parent 流水线（输出大块，用于回答时的完整上下文）
      │     策略链：STRUCTURE → RECURSIVE
      │
      └─→ Child 流水线（输出小块，用于检索时的精确匹配）
            策略链：SEMANTIC → RECURSIVE
  
    核心数据结构 ChunkCandidate 就是一个中间块：
  
    // ChunkCandidate.java - 承载每个中间结果
    {
        text: "本项目采用微服务架构...",     // 块内容
        sectionPath: "项目概述 > 背景",    // 所属章节路径
        structureNodeId: 1001,             // 来源结构节点
        canonicalPath: "/1-项目概述/1.1-背景",
        sourceType: ORIGINAL               // 来源类型
    }
  
  ---
    二、流水线执行器
  
    4 种策略可以串联组合，前一个的输出是后一个的输入：
  
    // DocumentStrategyServiceImpl.java:422
    private List<ChunkCandidate> executePipeline(
            List<ChunkCandidate> sourceList,         // 输入块
            List<SuperAgentDocumentStrategyStep> orderedSteps,  // 策略链
            DocumentStrategyPipelineTypeEnum pipelineType) {
  
        List<ChunkCandidate> currentChunks = sourceList;
      
        for (SuperAgentDocumentStrategyStep step : orderedSteps) {
            // 依次执行每个策略
            currentChunks = switch (strategyType) {
                case STRUCTURE -> applyStructureChunking(currentChunks, pipelineType);
                case RECURSIVE -> applyRecursiveChunking(currentChunks, pipelineType);
                case SEMANTIC  -> applySemanticChunking(currentChunks, pipelineType);
                case LLM       -> applyLlmChunking(currentChunks, pipelineType);
            };
            currentChunks = cleanupChunkList(currentChunks);  // 去重清理
        }
        return currentChunks;
    }
  
    举例：策略链 STRUCTURE → RECURSIVE
  
    原始文本 (5000字)
      ↓ applyStructureChunking → 按标题切出 3 个块
    [块A:800字] [块B:1800字] [块C:2400字]
      ↓ applyRecursiveChunking → 超长的块C再递归切
    [块A:800字] [块B:1800字] [块C1:1200字] [块C2:1200字]
  
  ---
    三、4 种策略逐一讲解
  
    策略1：STRUCTURE（按标题结构切分）
  
    核心思路：遇到标题就切一刀，每个标题段落成为一个块。
  
    // DocumentStrategyServiceImpl.java:516
    private List<ChunkCandidate> applyStructureChunking(
            String parsedText, DocumentStrategyPipelineTypeEnum pipelineType, ...) {

        List<ChunkCandidate> candidateList = new ArrayList<>();
        Deque<String> headingStack = new ArrayDeque<>();  // 标题层级栈
        StringBuilder currentChunk = new StringBuilder();
        String currentSectionPath = "";
      
        for (String line : parsedText.split("\n")) {
            String trimmed = line.trim();
            LineClassification classification = documentLineClassifier.classify(trimmed);
      
            if (classification.isHeading()) {
                // 遇到标题 → 先把之前的块存起来
                flushChunk(candidateList, currentSectionPath, currentChunk);
      
                // 更新标题栈（弹出同级及更低级的标题）
                while (headingStack.size() >= classification.level()) {
                    headingStack.removeLast();
                }
                headingStack.addLast(classification.title());
      
                // 拼接章节路径："项目概述 > 背景"
                currentSectionPath = String.join(" > ", headingStack);
                currentChunk.append(trimmed).append('\n');
                continue;
            }
      
            // 普通内容 → 追加到当前块
            currentChunk.append(line).append('\n');
        }
        // 最后一个块
        flushChunk(candidateList, currentSectionPath, currentChunk);
      
        // 如果一个标题都没找到（纯文本没有标题），降级到递归分块
        if (candidateList.isEmpty()) {
            return applyRecursiveChunking(...);
        }
        return candidateList;
    }
  
    实际效果：

    输入：
      # 项目概述
      本项目是一个...（800字）
      ## 背景
      随着...（300字）
      ## 目标
      本项目旨在...（500字）
      # 技术架构
      系统采用...（1800字）
  
    输出：
      块1: sectionPath="项目概述", text="# 项目概述\n本项目是一个...(800字)"
      块2: sectionPath="项目概述 > 背景", text="## 背景\n随着...(300字)"
      块3: sectionPath="项目概述 > 目标", text="## 目标\n本项目旨在...(500字)"
      块4: sectionPath="技术架构", text="# 技术架构\n系统采用...(1800字)"
  
    策略2：RECURSIVE（递归分块）
  
    核心思路：4 级分隔符逐步切分，每级切不开就用下一级。
  
    // DocumentStrategyServiceImpl.java:674
    private List<String> recursiveSplit(String text, int maxChars, int overlapChars) {
        if (text.length() <= maxChars) return List.of(text);  // 已经够小，直接返回
  
        // 第1级：按空行（段落）切
        List<String> paragraphList = splitByRegex(text, "\\n\\s*\\n");
        if (paragraphList.size() > 1) {
            return mergeAndSplit(paragraphList, maxChars, overlapChars);
        }
      
        // 第2级：按换行切
        List<String> lineList = splitByRegex(text, "\\n");
        if (lineList.size() > 1) {
            return mergeAndSplit(lineList, maxChars, overlapChars);
        }
      
        // 第3级：按句号切
        List<String> sentenceList = splitSentences(text);  // 按 。！？;. 切
        if (sentenceList.size() > 1) {
            return mergeAndSplit(sentenceList, maxChars, overlapChars);
        }
      
        // 第4级：固定窗口兜底（硬切）
        return fixedWindowSplit(text, maxChars, overlapChars);
    }
  
    mergeAndSplit 负责把小片段合并到接近 maxChars：
  
    private List<String> mergeAndSplit(List<String> segments, int maxChars, int overlapChars) {
        StringBuilder current = new StringBuilder();
        List<String> result = new ArrayList<>();
  
        for (String segment : segments) {
            // 单个片段超长 → 递归切分
            if (segment.length() > maxChars) {
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
                result.addAll(recursiveSplit(segment, maxChars, overlapChars));
                continue;
            }
      
            // 追加后超长 → 存当前块，重新开始
            if (current.length() + segment.length() > maxChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(segment).append('\n');
        }
      
        if (current.length() > 0) result.add(current.toString().trim());
      
        // 重叠处理：每个块开头带上一个块的末尾
        return applyOverlap(result, maxChars, overlapChars);
    }
  
    重叠机制：
  
    块1: "A公司成立于2020年，主营业务是人工智能。"
    块2: "主营业务是人工智能。公司总部位于北京。"  ← 开头重复了块1的末尾
    块3: "公司总部位于北京。员工规模超过500人。"
  
    好处：检索到块2时，上下文更完整，不会因为切分点在句中而丢失信息
  
    4 级递归的实际效果：
  
    输入：一段 3000 字的文本，中间没有空行和换行
  
    第1级：按空行切 → 只有1段 → 切不开
    第2级：按换行切 → 只有1行 → 切不开
    第3级：按句号切 → 切出10句 → 能切开
      → mergeAndSplit：把10句合并为 [块1:1800字, 块2:1200字]
  
    策略3：SEMANTIC（语义分块）
  
    核心思路：按相邻句子的 Jaccard 相似度判断语义边界，相似度突变处切分。
  
    // DocumentStrategyServiceImpl.java:633
    private List<ChunkCandidate> semanticSplit(ChunkCandidate candidate, ...) {
        // 1. 按句子切分
        List<String> sentences = splitSentences(candidate.getText());
  
        StringBuilder currentChunk = new StringBuilder();
        Set<String> currentTokenSet = new LinkedHashSet<>();  // 当前块的词集合
      
        for (String sentence : sentences) {
            Set<String> sentenceTokenSet = extractTokens(sentence);
      
            // 计算 Jaccard 相似度
            double similarity = jaccard(currentTokenSet, sentenceTokenSet);
      
            // 切分条件：超过最大长度 OR 相似度低于阈值
            boolean exceedMax = currentChunk.length() + sentence.length() > maxChars;
            boolean semanticBreak = currentChunk.length() >= minChars
                && similarity < similarityThreshold;  // 默认 0.35
      
            if (currentChunk.length() > 0 && (exceedMax || semanticBreak)) {
                // 当前块满了或语义变了 → 切一刀
                result.add(cloneChunkCandidate(candidate, currentChunk.toString()));
                currentChunk.setLength(0);
                currentTokenSet.clear();
            }
      
            currentChunk.append(sentence);
            currentTokenSet.addAll(sentenceTokenSet);
        }
    }

    Jaccard 相似度计算：
  
    private double jaccard(Set<String> left, Set<String> right) {
        // 交集大小 / 并集大小
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return (double) intersection.size() / union.size();
    }
  
    实际效果：
  
    句1: "Spring Boot 是一个Java框架"      tokens={spring,boot,java,framework,是,一,个,框架}
    句2: "Spring Boot 简化了配置过程"        tokens={spring,boot,简化,了,配置,过程}
      → Jaccard = 4/12 = 0.33 < 0.35 → 语义断点！切分
  
    句3: "配置过程包括自动配置和起步依赖"     tokens={配置,过程,包括,自动,和,起步,依赖}
    句4: "自动配置能根据类路径自动装配Bean"  tokens={自动,配置,能,根据,类路径,装配,Bean}
      → Jaccard = 4/14 = 0.29 < 0.35 → 又一个断点
  
    词集合怎么提取：
  
    private Set<String> extractTokens(String text) {
        // 英文：按单词提取
        Matcher matcher = ENGLISH_WORD_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) tokenSet.add(matcher.group());  // "spring", "boot"
  
        // 中文：逐字提取
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) {  // 中文字符范围
                tokenSet.add(String.valueOf(c));  // "是", "一", "个"
            }
        }
        return tokenSet;
    }
  
    策略4：LLM（大模型智能分块）
  
    核心思路：让大模型判断切分点，失败时回退到语义分块。
  
    // DocumentStrategyServiceImpl.java:877
    private List<String> llmSplit(ChatModel chatModel, String sourceText) {
        String prompt = """
            你是 RAG 文档切块助手。
            请把下面文本切成适合知识检索的若干片段，并严格返回 JSON 数组字符串。
            要求：
            1. 每个片段尽量语义完整。
            2. 不要输出解释文字。
            3. 返回格式示例：["片段1","片段2"]

            文本如下：
            """ + sourceText;
      
        try {
            String content = ChatClient.builder(chatModel).build()
                .prompt().user(prompt).call().content();
      
            String jsonArray = extractJsonArray(content);  // 提取 [...]
            List<String> chunks = objectMapper.readValue(jsonArray, ...);
            return chunks.stream().filter(StrUtil::isNotBlank).toList();
        } catch (Exception e) {
            log.warn("大模型智能切块失败，回退到语义切块");
            return List.of();  // 返回空 → 外层会自动走 semanticSplit
        }
    }
  
    超长文本处理：先递归切到 llmMaxChars，再逐段发给 LLM
  
    // 如果文本超过 LLM 处理上限，先粗切再精切
    int llmMaxChars = resolveLlmMaxChars(pipelineType);
    List<String> sourceTextList = candidate.getText().length() > llmMaxChars
        ? recursiveSplit(candidate.getText(), llmMaxChars, 0)  // 先粗切
        : List.of(candidate.getText());
  
    for (String sourceText : sourceTextList) {
        List<String> llmChunks = llmSplit(chatModel, sourceText);
        if (llmChunks.isEmpty()) {
            // LLM 失败 → 回退语义分块
            semanticSplit(cloneChunkCandidate(candidate, sourceText), pipelineType);
        }
    }
  
  ---
    四、Parent-Child 怎么串起来
  
    // DocumentStrategyServiceImpl.java:184 - buildParentBlocks 核心流程
    public List<ParentBlockCandidate> buildParentBlocks(...) {
  
        // ① 读取解析阶段存好的结构节点
        List<StructureNode> structureNodes = structureNodeService.listDocumentNodes(documentId, taskId);
      
        // ② Parent 流水线：生成父块种子
        //    策略链示例：STRUCTURE → RECURSIVE
        List<ChunkCandidate> parentSeedList = buildParentSeedList(parsedText, parentSteps, structureNodes);
      
        for (ChunkCandidate parentSeed : parentSeedList) {
      
            // ③ Child 流水线：对每个父块生成子块
            //    策略链示例：SEMANTIC → RECURSIVE
            List<ChunkCandidate> childSeedList = buildChildSeedList(parentSeed, childSteps, structureNodes);
      
            // ④ 打包成 ParentBlockCandidate
            parentBlockList.add(new ParentBlockCandidate(
                parentSeed.sectionPath,
                parentSeed.structureNodeId,
                parentSeed.text,          // 父块内容（大）
                childSeedList             // 子块列表（小）
            ));
        }
        return parentBlockList;
    }
  
    完整数据流：

    原始文本 (5000字)
      │
      │  Parent 流水线：STRUCTURE → RECURSIVE
      ▼
    父块1 [800字, sectionPath="项目概述"]
    父块2 [1800字, sectionPath="技术架构"]
    父块3 [1200字] + 父块4 [1200字]  ← 原"部署说明"2400字，递归再切
      │
      │  Child 流水线：SEMANTIC → RECURSIVE（对每个父块独立执行）
      ▼
    父块1 [800字]
      └─→ 子块1-1 [300字, sectionPath="项目概述 > 背景"]
      └─→ 子块1-2 [500字, sectionPath="项目概述 > 目标"]

    父块2 [1800字]
      └─→ 子块2-1 [400字]
      └─→ 子块2-2 [500字]
      └─→ 子块2-3 [450字]
      └─→ 子块2-4 [450字]
  
    父块3 [1200字]
      └─→ 子块3-1 [480字]
      └─→ 子块3-2 [480字]
      └─→ 子块3-3 [240字]
  
  ---
    五、策略推荐（AI 自动选择）
  
    系统不是固定用某一种策略，而是根据文档特征自动推荐最优组合：
  
    // DocumentStrategyServiceImpl.java:89
    public DocumentStrategyPlanDraft recommendStrategy(Document doc, DocumentAnalysisResult analysis) {
  
        // 判断条件
        boolean structureRecommended = shouldUseStructure(fileType, analysis);
        boolean recursiveRecommended = shouldUseRecursive(analysis);
        boolean semanticRecommended = shouldUseSemantic(analysis);
        boolean llmRecommended = shouldUseLlm(analysis);
      
        // Parent 流水线
        if (structureRecommended) {
            parentSteps = [STRUCTURE]     // 有标题结构 → 优先用结构分块
        } else {
            parentSteps = [RECURSIVE]     // 没结构 → 递归兜底
        }
      
        // Child 流水线
        if (llmRecommended) {
            childSteps = [LLM, RECURSIVE]       // 低质量文档 → LLM 增强
        } else if (semanticRecommended) {
            childSteps = [SEMANTIC, RECURSIVE]   // 正常文档 → 语义 + 递归
        } else {
            childSteps = [RECURSIVE]             // 短文档 → 纯递归
        }
    }
  
    推荐依据：
  
    // 有明显标题结构的文档类型 + 标题数 >= 2
    boolean shouldUseStructure = (PDF || DOCX || MD || HTML) && headingCount >= 2;
  
    // 文档够长 或 有超长段落
    boolean shouldUseRecursive = charCount >= 800 || maxParagraphLength >= 800;
  
    // 文档够长 + 段落数 >= 3 + 质量中等以上
    boolean shouldUseSemantic = charCount >= 300 && paragraphCount >= 3 && quality >= MEDIUM;
  
    // 开启了LLM推荐 + 文档质量低
    boolean shouldUseLlm = recommendLlmWhenLowQuality && quality == LOW;

  ---
    六、总结：策略组合矩阵

    ┌────────────┬─────────────┬──────────────────────┬──────────────────────────┐
    │    场景    │ Parent 策略 │      Child 策略      │         适用文档         │
    ├────────────┼─────────────┼──────────────────────┼──────────────────────────┤
    │ 技术文档   │ STRUCTURE   │ SEMANTIC + RECURSIVE │ 有标题结构的 PDF/DOCX/MD │
    ├────────────┼─────────────┼──────────────────────┼──────────────────────────┤
    │ 长文章     │ RECURSIVE   │ SEMANTIC + RECURSIVE │ 无标题但较长的文本       │
    ├────────────┼─────────────┼──────────────────────┼──────────────────────────┤
    │ 短文档     │ RECURSIVE   │ RECURSIVE            │ 几百字的短文本           │
    ├────────────┼─────────────┼──────────────────────┼──────────────────────────┤
    │ 低质量文档 │ STRUCTURE   │ LLM + RECURSIVE      │ OCR 乱码、格式混乱的文档 │
    └────────────┴─────────────┴──────────────────────┴──────────────────────────┘
  
    一句话：4 种策略不是互斥的，而是流水线上的工序，可以串联组合。系统根据文档特征自动推荐最优工序链，用户也可以手动调整。
  
- [ ] 理解 Parent-Child 的设计动机

- [ ] 知道 Jaccard 相似度的计算方法

---

### 第9章 向量化与索引

#### 9.1 向量化流程

```java
// DefaultDocumentVectorGateway.java
@Service
public class DefaultDocumentVectorGateway implements DocumentVectorGateway {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Override
    public void vectorizeAndStore(List<Chunk> chunks, Long documentId) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getObject();

        // 批量向量化，每批 10 个
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<Chunk> batch = chunks.subList(i,
                    Math.min(i + batchSize, chunks.size()));

            // 提取文本列表
            List<String> texts = batch.stream()
                    .map(Chunk::getContent)
                    .toList();

            // 调用 Qwen3 Embedding API 获取向量
            List<float[]> embeddings = embeddingModel.embed(texts);

            // 存入 PGVector
            for (int j = 0; j < batch.size(); j++) {
                storeToPgVector(batch.get(j), embeddings.get(j), documentId);
            }
        }
    }

    private void storeToPgVector(Chunk chunk, float[] embedding, Long documentId) {
        String embeddingJson = Arrays.toString(embedding);  // "[0.1, 0.2, ...]"

        jdbcTemplate.update("""
                INSERT INTO document_embedding (id, document_id, chunk_id, embedding, metadata)
                VALUES (?, ?, ?, CAST(? AS vector), CAST(? AS jsonb))
                ON CONFLICT (id) DO UPDATE SET
                    embedding = EXCLUDED.embedding,
                    metadata = EXCLUDED.metadata
                """,
                chunk.getId().toString(),
                documentId,
                chunk.getId(),
                embeddingJson,
                buildMetadataJson(chunk)
        );
    }
}
```

#### 9.2 索引消费者

```java
// DocumentIndexConsumer.java
@KafkaListener(topics = "${document.kafka.index-topic}")
public void onIndexMessage(ConsumerRecord<String, String> record) {
    Long documentId = Long.parseLong(record.value());
    log.info("收到索引任务: documentId={}", documentId);

    documentMapper.updateStatus(documentId, DocumentStatus.INDEXING);

    // 1. 查询已分块数据
    List<Chunk> chunks = chunkMapper.selectByDocumentId(documentId);

    // 2. 向量化并存储
    vectorGateway.vectorizeAndStore(chunks, documentId);

    // 3. 同时写入 Elasticsearch（关键词检索用）
    elasticsearchGateway.indexChunks(chunks, documentId);

    documentMapper.updateStatus(documentId, DocumentStatus.INDEXED);
}
```

**学习检查点**:
- [ ] 理解 Embedding 模型将文本转为向量的过程
- [ ] 知道 `ON CONFLICT DO UPDATE` 的幂等性作用
- [ ] 能说清为什么同时用 PGVector 和 Elasticsearch

---

## 阶段三：智能问答流水线（第3周）

● 在线问答完整流程（从用户提问到返回答案）

  假设用户在文档问答系统里输入："父子分块策略是怎么实现的？"

---
  第1步：接收问题

  前端通过 SSE（服务端推送）建立长连接，把用户问题传给后端。后端拿到问题后，先做基本校验：是不是空白、是不是太短。校验通
  过后进入下面的流程。

---
  第2步：加载对话记忆

  系统从数据库里加载这个会话的历史记录，包括：
  - 结构化记忆：之前讨论过哪些主题、提到过哪些章节、用户偏好等（用 LLM 从历史对话中提取的摘要）
  - 最近几轮对话原文：最近 4 轮对话的原始内容

  把这些拼成一段"历史上下文"，后面改写问题要用到。

---
  第3步：查询改写

  用户的问题经常存在这些问题：
  - 指代不清：上一轮问了分块策略，这一轮问"它支持自定义吗"——"它"指的是什么？
  - 多个问题塞在一起："分块策略有哪些？向量化的模型是什么？"
  - 口语化省略："那个模型支持多少种语言"——哪个模型？

  改写做的事情：
  1. 把历史上下文和当前问题一起喂给 LLM
  2. LLM 输出一个 JSON，包含改写后的独立问题 + 是否需要拆分 + 子问题列表
  3. 系统对 LLM 输出做校验和后处理（防止 LLM 自由发挥过度扩展）

  改写前："它支持自定义吗"
  改写后："父子分块策略支持自定义大小吗"

  改写有几个严格约束：只做指代消解，不扩展，不添加假设，保留所有限制条件。如果问题本身已经很完整（长且没有历史上下文），
  就跳过改写省一次 LLM 调用。

---
  第4步：第一级路由——选文档

  系统里可能有几十篇文档，不可能每篇都搜。这一步决定搜哪篇文档。

  系统维护了三层知识索引：

  知识域（Scope）：比如"技术文档"、"产品手册"、"FAQ"。每个域有名称、描述、别名、示例问题。

  主题（Topic）：域下面的细分，比如技术文档域下有"分块策略"、"向量化"、"检索"等主题。

  文档（Document）：具体的文档，每篇有名称、所属域、标签、内容摘要、核心主题、示例问题。

  路由的过程：
  1. 把用户问题向量化
  2. 跟所有 Scope 的描述信息算余弦相似度，排序
  3. 跟所有 Topic 的描述信息算相似度，匹配到 top Scope 的 Topic 额外加分
  4. 跟所有 Document 的描述信息算相似度，匹配 top Scope 和 top Topic 的文档额外加分
  5. 三种信号加权：语义相似度（主要）+ BM25 关键词匹配（辅助）+ 实体关键词命中（辅助）

  最后得到一个排序后的文档列表和置信度分数。如果置信度太低（<
  0.55），说明系统不确定用户要问哪篇文档，后续可能触发澄清模式。

---
  第5步：第二级路由——选执行方式

  确定了目标文档后，系统要决定用什么方式回答这个问题。不同问题有不同的最优解法：

  情况 A：用户问的是结构类问题
  比如"第三章后面是什么"、"文档包含哪些章节"
  → 走 GRAPH_ONLY 模式：直接查 Neo4j 图数据库的关系边，不需要向量检索，不需要调 LLM，延迟最低

  情况 B：用户问的是某个具体步骤/条目
  比如"第三步怎么操作"、"第2项是什么"
  → 走 GRAPH_THEN_EVIDENCE 模式：先用图库定位到具体节点，直接读节点的内容文本返回，不需要向量检索，不需要调 LLM

  情况 C：通用知识问答
  比如"父子分块策略是怎么实现的"
  → 走 RETRIEVAL 模式：完整 RAG 流程（下面详细讲）

  情况 D：分析性问题
  包含"为什么"、"区别"、"关系"、"分析"等词的问题
  → 强制走 RETRIEVAL 模式，不能走图谱路径（图库只能查结构关系，不能做推理分析）

  这一步还会尝试解析用户问的是文档中的哪个章节（通过正则匹配章节编号、ES
  导航索引搜索、本地结构打分、图库兜底四级级联），解析结果作为后续检索的范围缩小提示。

---
  第6步：混合检索（RETRIEVAL 模式的核心）

  假设走的是最常用的 RETRIEVAL 模式，检索分三轮：

  第一轮：向量检索
  1. 把改写后的问题通过 Embedding 模型（BGE-M3）转成向量
  2. 在 Milvus 向量库里做近似最近邻搜索（ANN）
  3. 同时查父子块：先查子块（精确），拿到命中的子块对应的父块（完整上下文）
  4. 召回一批候选文档块

  第二轮：关键词检索
  1. 把问题分词
  2. 在 Elasticsearch 里做 BM25 全文搜索
  3. 用 bool query 多字段匹配（标题×3、内容×1、锚文本×2）
  4. 召回另一批候选文档块

  第三轮：两路结果融合
  1. RRF（Reciprocal Rank Fusion）：向量检索和关键词检索各自排出名次，RRF 按 1/(k+rank)
    公式给每个结果算融合分数，合并去重
  2. Rerank 精排：调 SiliconFlow 的 BGE-Reranker 模型，把问题和每个候选块一起输入，得到一个更精确的相关性分数，重新排序

---
  第7步：组装上下文

  检索出来的块不能全部塞给 LLM（有 token 上限），需要做预算管理：

  1. 按 Rerank 得分从高到低排序
  2. 逐个加入上下文，累计 token 数
  3. 达到预算上限时停止
  4. 如果有父子块，优先保留父块（内容更完整），父块内高亮命中的子块位置

  最终组装成一段结构化的上下文文本。

---
  第8步：生成答案

  把系统提示词 + 组装好的上下文 + 用户原始问题一起发给 LLM，要求 LLM 只基于给定上下文回答，不要编造。LLM 通过 SSE
  流式输出，逐 token 推送给前端用户看到打字机效果。

  如果检索结果为空（什么都没搜到），系统直接返回"未找到相关证据"的兜底回复，跳过 LLM 调用，省时间和成本。

---
  第9步：后处理

  答案生成完毕后：
  1. 推荐问题：异步调用 LLM，根据当前问答生成 3 个相关问题推荐给用户（"你可能还想知道..."）
  2. 写入记忆：把本轮问答的关键信息提取为结构化记忆，存入数据库，供后续对话的改写步骤使用
  3. 路由追踪：记录本次路由决策（选了哪个文档、用了什么模式、置信度多少），用于后续分析优化

---
  整体流程图

  用户提问
    │
    ▼
  ① 接收 + 基本校验
    │
    ▼
  ② 加载对话记忆（结构化记忆 + 最近几轮原文）
    │
    ▼
  ③ 查询改写（LLM 指代消解，可能拆分子问题）
    │
    ▼
  ④ 第一级路由：选文档（Scope → Topic → Document，三级打分排序）
    │
    ▼
  ⑤ 第二级路由：选执行方式（GRAPH_ONLY / GRAPH_THEN_EVIDENCE / RETRIEVAL）
    │
    ├── GRAPH_ONLY ──────→ 直接查 Neo4j 返回 → 结束
    ├── GRAPH_THEN_EVIDENCE → 图库定位 + 读内容返回 → 结束
    └── RETRIEVAL ──────→ ↓
    │
    ▼
  ⑥ 混合检索（向量检索 + 关键词检索 → RRF 融合 → Rerank 精排）
    │
    ▼
  ⑦ 组装上下文（Token 预算管理，父子块优化）
    │
    ▼
  ⑧ LLM 生成答案（SSE 流式输出）
    │
    ▼
  ⑨ 后处理（推荐问题 + 写入记忆 + 路由追踪）

---
  几个设计亮点

  不依赖 LLM 做路由决策：两级路由都是规则+向量匹配，只有改写和答案生成调 LLM。这样延迟可控，不会因为 LLM
  慢导致整个流程卡住。

  检索不到就不调 LLM：如果向量检索和关键词检索都没结果，直接返回兜底回复，不浪费 LLM 调用。

  图谱路径跳过向量检索：结构类问题直接查 Neo4j，步骤类问题图库定位+读内容，完全绕过向量检索和 LLM，响应最快。

  改写有严格约束和兜底：LLM 挂了用规则切割，LLM 输出不合理会被后处理纠正，不会因为改写出错导致整个流程崩溃。

### 第10章 SSE 流式响应

#### 10.1 控制器入口

```java
// BusinessChatController.java
@RestController
@RequestMapping("/api/chat")
public class BusinessChatController {

    private final BusinessChatService chatService;

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        // SSE 协议要求 Content-Type: text/event-stream
        // 返回 Reactor Flux 实现流式推送
        return chatService.streamAnswer(
                request.getSessionId(),
                request.getQuestion(),
                request.getSystemPrompt()
        );
    }

    @PostMapping("/stop")
    public ApiResponse<Void> stopChat(@RequestBody SessionRequest request) {
        chatService.stopSession(request.getSessionId());
        return ApiResponse.success(null);
    }
}
```

#### 10.2 Sinks.Many 作为消息通道

```java
// BusinessChatService.java 核心片段
public class BusinessChatService {

    /** 会话级消息通道 */
    private final Map<String, TaskInfo> activeSessions = new ConcurrentHashMap<>();

    public Flux<String> streamAnswer(String sessionId, String question, String systemPrompt) {
        // 创建 Sinks.Many 作为多播通道
        Sinks.Many<String> sink = Sinks.many().multicast()
                .onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

        TaskInfo taskInfo = new TaskInfo(sessionId, sink, ...);
        activeSessions.put(sessionId, taskInfo);

        // 异步执行问答流程
        CompletableFuture.runAsync(() -> executeAnswerFlow(taskInfo, question));

        // 返回 Flux 供 SSE 推送
        return sink.asFlux();
    }

    private void executeAnswerFlow(TaskInfo taskInfo, String question) {
        try {
            // 推送思考事件
            sink.tryEmitNext(sseEvent("thinking", "正在检索知识库..."));

            // 执行 RAG 检索
            Flux<String> answerFlux = ragExecutor.execute(taskInfo);

            // 订阅答案流，转发到 SSE 通道
            answerFlux.subscribe(
                token -> sink.tryEmitNext(sseEvent("answer", token)),
                error -> sink.tryEmitNext(sseEvent("error", error.getMessage())),
                () -> {
                    // 推送引用来源
                    sink.tryEmitNext(sseEvent("references", toJson(references)));
                    sink.tryEmitComplete();
                }
            );
        } catch (Exception e) {
            sink.tryEmitNext(sseEvent("error", e.getMessage()));
            sink.tryEmitComplete();
        }
    }

    private String sseEvent(String type, String data) {
        return "event:" + type + "\ndata:" + data + "\n\n";
    }
}
```

**SSE 协议格式**:
```
event:thinking
data:正在检索知识库...

event:answer
data:根据

event:answer
data:文档内容

event:answer
data:，该问题的答案是...

event:references
data:[{"documentName":"xxx","sectionPath":"第1章"}]

```

**学习检查点**:
- [ ] 理解 SSE vs WebSocket 的区别和适用场景
- [ ] 知道 Sinks.Many 的背压机制
- [ ] 能说出 SSE 事件格式的规范

---

### 第11章 查询改写

#### 11.1 改写服务

```java
// ChatQueryRewriteService.java
@Service
public class ChatQueryRewriteService {

    private final ObservedChatModelService chatModelService;

    public RagRewriteResult rewrite(String sessionId, String question,
                                     List<ConversationExchangeView> history) {
        // 判断是否需要改写
        if (!needsRewrite(question, history)) {
            return RagRewriteResult.noRewrite(question);
        }

        // 构建改写 Prompt
        String prompt = buildRewritePrompt(question, history);

        // 调用大模型
        String response = chatModelService.callText("query_rewrite", null, prompt, null);

        // 解析 JSON 响应
        return parseRewriteResponse(response, question);
    }

    private boolean needsRewrite(String question, List<ConversationExchangeView> history) {
        boolean hasHistory = !history.isEmpty();
        int questionLen = question.trim().length();

        // 无历史：问题过短需要改写（<8字符）
        if (!hasHistory) return questionLen < 8;

        // 有历史：问题过短需要改写（<18字符），因为可能有省略
        return questionLen < 18;
    }

    private String buildRewritePrompt(String question, List<ConversationExchangeView> history) {
        return """
                你是一个查询改写助手。根据对话历史和当前问题，进行查询改写。

                规则：
                1. 将省略、指代补全为完整表述
                2. 如果问题包含多个子问题，should_split 设为 true
                3. 返回 JSON: {"rewrite":"改写后","should_split":true/false,"sub_questions":["子问题1"]}

                对话历史：
                %s

                当前问题：%s
                """.formatted(formatHistory(history), question);
    }
}
```

#### 11.2 保守拆分策略

```java
private RagRewriteResult parseRewriteResponse(String response, String originalQuestion) {
    // 解析 LLM 返回的 JSON
    RewriteJson json = objectMapper.readValue(extractJson(response), RewriteJson.class);

    // 保守拆分：LLM 说要拆分，但代码检查是否有明确的多问题标记
    if (json.shouldSplit()) {
        boolean hasExplicitMarkers = originalQuestion.contains("？？")
                || originalQuestion.contains(";")
                || originalQuestion.contains("分别")
                || NUMERIC_PATTERN.matcher(originalQuestion).find();

        if (!hasExplicitMarkers) {
            // 没有明确标记，拒绝 LLM 的拆分建议
            json.setShouldSplit(false);
            json.setSubQuestions(List.of());
        }
    }

    // 回退：按分隔符拆分
    if (json.getSubQuestions() == null || json.getSubQuestions().isEmpty()) {
        List<String> fallback = Arrays.stream(
                originalQuestion.split("[?；\\n]"))
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .toList();
        json.setSubQuestions(fallback);
    }

    return new RagRewriteResult(json.getRewrite(), json.getSubQuestions());
}
```

**为什么保守拆分？**
- LLM 可能过度拆分，导致检索分散、答案不完整
- 只有用户明确使用了 `？？`、`分别`、`第一...第二` 等标记时才拆分
- 宁可检索范围大一些，也不要拆得太碎

**学习检查点**:
- [ ] 理解查询改写解决的核心问题（省略、指代、模糊）

    问题一：指代消解（最核心）

    多轮对话中用户问题包含代词，直接拿去检索会命中无关内容。

    用户: "文档分块策略是怎么实现的"
    助手: [回答了4种分块策略]

    用户: "它支持自定义大小吗"   ← "它" 是什么？向量检索根本搜不到

    改写后："文档分块策略支持自定义大小吗" — 代词替换为实际名词，检索就能命中。

    项目代码中 needsRewrite 的判断逻辑体现了这一点：没有历史上下文 + 问题已经很长（>=18字符） →
    跳过改写，因为这种问题大概率是自包含的，不需要消解。

  ---
    问题二：多问题拆分

    用户一句话塞了多个独立问题，一次检索只能命中一个方向。

    用户: "分块策略有哪些？向量化的模型是什么？怎么处理解析失败的情况？"

    项目用 looksLikeExplicitMultiQuestion 检测这种场景（>=2个问号、分号、"分别"等），然后 LLM
    拆成多个子问题分别检索，最后合并结果。上限 maxSubQuestions=4。

  ---
    问题三：口语化 / 省略信息补全

    用户的问题经常省略主语、上下文，直接检索效果差。

    历史: "我们用的向量模型是 BGE-M3"
    用户: "那个模型支持多少种语言"

    改写后："BGE-M3 向量模型支持多少种语言" — 补全了缺失的主语。

  ---
    项目中的设计取舍

    ┌────────────────────────────────────────────────┬────────────────────────────────────────────────┐
    │                     设计点                     │                      体现                      │
    ├────────────────────────────────────────────────┼────────────────────────────────────────────────┤
    │ 低温度 temperature=0.1, topP=0.3               │ 改写要求保守，不允许自由发挥，只做消解不做扩展 │
    ├────────────────────────────────────────────────┼────────────────────────────────────────────────┤
    │ Prompt 约束 "只做指代消解，不扩展，不添加假设" │ 防止 LLM 把问题改得偏离用户原意                │
    ├────────────────────────────────────────────────┼────────────────────────────────────────────────┤
    │ 跳过机制 needsRewrite                          │ 已经是完整独立问题时省掉一次 LLM 调用          │
    ├────────────────────────────────────────────────┼────────────────────────────────────────────────┤
    │ 规则兜底 ruleBasedSplit                        │ LLM 挂了也能按标点符号拆分，保证不阻塞主流程   │
    ├────────────────────────────────────────────────┼────────────────────────────────────────────────┤
    │ 不带 system prompt                             │ 改写是纯功能性调用，不需要角色设定，减少 token │
    └────────────────────────────────────────────────┴────────────────────────────────────────────────┘

  ---
    一句话总结：查询改写的核心目标是把依赖上下文的、口语化的、多意图的用户问题，转换成独立的、精确的、可直接用于向量检索的问题。它不改变用户意图，只做"翻译"——把人能理解的问题翻译成检索系统能理解的问题。

- [ ] 能说清为什么采用保守拆分策略

- [ ] 知道改写 Prompt 的 JSON 输出格式设计

---

### 第12章 文档路由决策

#### 12.1 路由器

```java
// DocumentQuestionRouter.java
@Service
public class DocumentQuestionRouter {

    /** 根据问题特征决定执行模式 */
    public DocumentNavigationDecision route(Long documentId,
                                             String originalQuestion,
                                             RagRewriteResult rewriteResult) {

        String routeText = originalQuestion + " " + rewrittenQuestion;

        // 模式1: 纯图查询（章节位置、目录类问题）
        if (asksAdjacency(routeText) || asksOutline(routeText)) {
            return buildDecision(ExecutionMode.GRAPH_ONLY, ...);
        }

        // 模式2: 图优先再取证（第几步、第几项类问题）
        if (asksItemLookup(routeText)) {
            return buildDecision(ExecutionMode.GRAPH_THEN_EVIDENCE, ...);
        }

        // 模式3: 混合检索（普通问答）
        return buildDecision(ExecutionMode.RETRIEVAL, ...);
    }

    /** 是否问章节位置（上一节、下一节） */
    private boolean asksAdjacency(String question) {
        List<String> hints = List.of("上一节", "下一节", "前一节", "后一节",
                "上一个章节", "下一个章节", "属于哪个章节", "章节位置");
        return hints.stream().anyMatch(question::contains);
    }

    /** 是否问目录结构 */
    private boolean asksOutline(String question) {
        List<String> hints = List.of("包含哪些章节", "有哪些章节", "有哪些小节",
                "包含哪些小节", "章节列表", "目录");
        return hints.stream().anyMatch(question::contains);
    }

    /** 是否问具体步骤/条目 */
    private boolean asksItemLookup(String question) {
        List<String> hints = List.of("哪一步", "哪一项", "第几步", "第几项",
                "具体步骤", "步骤中的");
        return hints.stream().anyMatch(question::contains);
    }
}
```

#### 12.2 章节定位

当路由需要定位具体章节时，按优先级尝试：

```java
private GraphSection resolveSection(Long documentId, String original, String rewritten) {
    // 1. 按章节编号匹配（如 "3.2.1"）
    GraphSection byCode = resolveBySectionCode(documentId, original, rewritten);
    if (byCode != null) return byCode;

    // 2. 按 ES 导航索引搜索
    GraphSection byIndex = resolveByNavigationIndex(documentId, original, rewritten);
    if (byIndex != null) return byIndex;

    // 3. 按本地结构评分匹配
    List<String> phrases = buildSectionPhrases(original, rewritten);
    GraphSection byLocal = resolveByLocalStructure(documentId, phrases);
    if (byLocal != null) return byLocal;

    // 4. 图服务最佳匹配
    return graphService.findBestSection(documentId, rewritten, "");
}
```

**学习检查点**:
- [ ] 能说出 3 种执行模式的触发条件
- [ ] 理解章节定位的多级降级策略
- [ ] 知道 `GRAPH_ONLY` 模式跳过向量检索的原因

---

### 第13章 多通道检索

#### 13.1 检索引擎核心

```java
// RagRetrievalEngine.java
@Service
public class RagRetrievalEngine {

    /** 双通道混合检索入口 */
    public RagRetrievalContext retrieve(ConversationExecutionPlan plan,
                                         ConversationTraceRecorder recorder) {

        List<String> subQuestions = plan.getRetrievalQuestionPlan().getSubQuestions();

        // 并行处理所有子问题
        List<CompletableFuture<SubQuestionEvidence>> futures = subQuestions.stream()
                .map(sq -> CompletableFuture.supplyAsync(
                        () -> retrieveForSubQuestion(sq, plan),
                        retrievalExecutor))
                .toList();

        // 收集结果
        List<SubQuestionEvidence> evidenceList = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        return new RagRetrievalContext(evidenceList, plan);
    }

    /** 单个子问题的检索流程（6步） */
    private SubQuestionEvidence retrieveForSubQuestion(String subQuestion,
                                                        ConversationExecutionPlan plan) {
        // Step 1: 多通道并行检索
        List<ChannelResult> channelResults = parallelChannelSearch(subQuestion, plan);

        // Step 2: Evidence Gate 证据过滤
        List<ChannelResult> gated = applyEvidenceGate(channelResults);

        // Step 3: RRF 融合排序
        List<Document> fused = rrfFusion(gated);

        // Step 4: 父块提升（Child → Parent）
        List<Document> elevated = elevateToParent(fused);

        // Step 5: Rerank 重排序
        List<Document> reranked = rerankPostProcess(subQuestion, elevated);

        // Step 6: Top-K 截断
        return topK(reranked, plan.getFinalTopK());
    }
}
```

#### 13.2 并行通道检索

```java
private List<ChannelResult> parallelChannelSearch(String question,
                                                    ConversationExecutionPlan plan) {
    // 两个通道并发执行
    CompletableFuture<ChannelResult> vectorFuture = CompletableFuture.supplyAsync(
            () -> vectorChannelSearch(question, plan), executor);

    CompletableFuture<ChannelResult> keywordFuture = CompletableFuture.supplyAsync(
            () -> keywordChannelSearch(question, plan), executor);

    // 等待两个通道完成（带超时）
    return CompletableFuture.allOf(vectorFuture, keywordFuture)
            .thenApply(v -> List.of(vectorFuture.join(), keywordFuture.join()))
            .orTimeout(10, TimeUnit.SECONDS)
            .exceptionally(e -> {
                log.warn("通道检索超时: {}", e.getMessage());
                // 超时时返回已完成的通道结果
                List<ChannelResult> partial = new ArrayList<>();
                if (vectorFuture.isDone()) partial.add(vectorFuture.join());
                if (keywordFuture.isDone()) partial.add(keywordFuture.join());
                return partial;
            })
            .join();
}
```

**向量通道** (PGVector):
```java
private ChannelResult vectorChannelSearch(String question, ...) {
    // 1. 将问题转为向量
    float[] queryEmbedding = embeddingModel.embed(question);

    // 2. PGVector 近邻搜索
    List<Document> results = jdbcTemplate.query("""
            SELECT id, chunk_id, embedding, metadata,
                   1 - (embedding <=> CAST(? AS vector)) AS similarity
            FROM document_embedding
            WHERE document_id = ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """, rowMapper, queryEmbedding, documentId, queryEmbedding, topK);

    return new ChannelResult("vector", results);
}
```

**关键词通道** (Elasticsearch):
```java
private ChannelResult keywordChannelSearch(String question, ...) {
    // BM25 全文搜索
    SearchRequest request = SearchRequest.of(s -> s
            .index("document_chunks")
            .query(q -> q.match(m -> m
                    .field("content")
                    .query(question)
                    .analyzer("smartcn")  // 中文分词
            ))
            .size(topK)
    );

    SearchResponse<ChunkDocument> response = elasticsearchClient.search(request, ChunkDocument.class);
    List<Document> results = response.hits().hits().stream()
            .map(hit -> toDocument(hit))
            .toList();

    return new ChannelResult("keyword", results);
}
```

#### 13.3 Evidence Gate 证据门控

过滤掉质量差的检索结果：

```java
private List<ChannelResult> applyEvidenceGate(List<ChannelResult> channels) {
    return channels.stream()
            .map(this::gateChannel)
            .filter(ch -> !ch.getDocuments().isEmpty())
            .toList();
}

private ChannelResult gateChannel(ChannelResult channel) {
    if ("vector".equals(channel.getName())) {
        // 向量通道：相似度必须 >= 阈值
        double threshold = ragProperties.getVector().getMinSimilarity();  // 0.5
        List<Document> filtered = channel.getDocuments().stream()
                .filter(doc -> (double) doc.getMetadata().get("similarity") >= threshold)
                .toList();
        return new ChannelResult(channel.getName(), filtered);
    }

    if ("keyword".equals(channel.getName())) {
        // 关键词通道：得分必须 >= 最高分的一定比例
        if (channel.getDocuments().isEmpty()) return channel;
        double topScore = (double) channel.getDocuments().get(0).getMetadata().get("score");
        double floor = topScore * ragProperties.getKeyword().getRelativeFloor();  // 0.3
        List<Document> filtered = channel.getDocuments().stream()
                .filter(doc -> (double) doc.getMetadata().get("score") >= floor)
                .toList();
        return new ChannelResult(channel.getName(), filtered);
    }

    return channel;
}
```

**学习检查点**:
- [ ] 理解向量检索和关键词检索各自的优缺点
- [ ] 能说清 Evidence Gate 的作用（为什么需要门控）
- [ ] 知道 CompletableFuture 的超时保护机制

---

### 第14章 RRF 融合排序

#### 14.1 RRF 公式

**Reciprocal Rank Fusion**: 对多个排序列表进行融合，公式为：

```
RRF_score(d) = Σ 1 / (k + rank(d))
```

- `k` = 60（经验常数，降低高排名文档的权重差异）
- `rank(d)` = 文档 d 在某个通道中的排名（从 1 开始）
- 同一文档出现在多个通道时，得分累加

#### 14.2 代码实现

```java
private List<Document> rrfFusion(List<ChannelResult> channels) {
    int k = ragProperties.getRrf().getK();  // 60
    int candidateTopK = ragProperties.getRrf().getCandidateTopK();  // 80

    // 文档ID → 累积得分
    Map<String, Double> scoreMap = new HashMap<>();
    // 文档ID → 文档对象
    Map<String, Document> docMap = new LinkedHashMap<>();

    for (ChannelResult channel : channels) {
        List<Document> docs = channel.getDocuments();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String docId = doc.getId();
            int rank = i + 1;  // 排名从 1 开始

            // RRF 得分累加
            double rrfScore = 1.0 / (k + rank);
            scoreMap.merge(docId, rrfScore, Double::sum);
            docMap.putIfAbsent(docId, doc);
        }
    }

    // 按 RRF 得分降序排列，取候选池大小
    return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(candidateTopK)
            .map(entry -> {
                Document doc = docMap.get(entry.getKey());
                doc.getMetadata().put("rrfScore", entry.getValue());
                return doc;
            })
            .toList();
}
```

**举个例子**:
```
向量通道: [DocA(1st), DocB(2nd), DocC(3rd)]
关键词通道: [DocB(1st), DocD(2nd), DocA(3rd)]

RRF 得分 (k=60):
  DocA = 1/(60+1) + 1/(60+3) = 0.01639 + 0.01587 = 0.03226
  DocB = 1/(60+2) + 1/(60+1) = 0.01613 + 0.01639 = 0.03252
  DocC = 1/(60+3) = 0.01587
  DocD = 1/(60+2) = 0.01613

最终排序: DocB > DocA > DocD > DocC
```

**学习检查点**:
- [ ] 能手动计算 RRF 得分
- [ ] 理解 k=60 的作用（降低排名差异的放大效应）
- [ ] 知道为什么 RRF 不需要对原始得分做归一化

---

### 第15章 父块提升与 Rerank

#### 15.1 父块提升

检索到的是 Child 小块，需要提升到 Parent 大块以获取完整上下文：

```java
private List<Document> elevateToParent(List<Document> childDocs) {
    // 收集需要查询的 Parent ID
    Set<String> parentIds = childDocs.stream()
            .map(doc -> doc.getMetadata().get("parentChunkId").toString())
            .collect(Collectors.toSet());

    // 批量查询 Parent 块
    Map<String, Document> parentMap = chunkMapper.selectParentChunks(parentIds)
            .stream()
            .collect(Collectors.toMap(doc -> doc.getId(), Function.identity()));

    // 用 Parent 替换 Child（去重，保留最高分）
    Map<String, Document> elevated = new LinkedHashMap<>();
    for (Document child : childDocs) {
        String parentId = child.getMetadata().get("parentChunkId").toString();
        Document parent = parentMap.get(parentId);
        if (parent != null) {
            // 同一 Parent 可能被多个 Child 命中，保留最高分
            elevated.merge(parentId, parent, (existing, candidate) -> {
                double existingScore = (double) existing.getMetadata()
                        .getOrDefault("rrfScore", 0.0);
                double candidateScore = (double) candidate.getMetadata()
                        .getOrDefault("rrfScore", 0.0);
                return candidateScore > existingScore ? candidate : existing;
            });
        }
    }
    return new ArrayList<>(elevated.values());
}
```

#### 15.2 Rerank 重排序

使用 BAAI/bge-reranker-v2-m3 对候选文档做精排：

```java
// HttpDocumentRerankPostProcessor.java
@Service
public class HttpDocumentRerankPostProcessor implements DocumentPostProcessor {

    private final RestClient restClient;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (!rerankProperties.isEnabled()) return documents;

        // 构造请求
        Map<String, Object> body = Map.of(
            "model", rerankProperties.getModel(),  // "BAAI/bge-reranker-v2-m3"
            "query", query.text(),
            "documents", documents.stream().map(Document::getText).toList(),
            "top_n", rerankProperties.getTopN(),   // 10
            "return_documents", false
        );

        // 调用 SiliconFlow Rerank API
        Map<String, Object> response = restClient.post()
                .uri(rerankProperties.getUrl())     // https://api.siliconflow.cn/v1/rerank
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + rerankProperties.getApiKey())
                .body(body)
                .retrieve()
                .body(Map.class);

        // 解析结果，按 relevance_score 降序排列
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        return results.stream()
                .sorted(Comparator.comparingDouble(r -> -((Number) r.get("relevance_score")).doubleValue()))
                .limit(topN)
                .map(result -> {
                    int index = ((Number) result.get("index")).intValue();
                    double score = ((Number) result.get("relevance_score")).doubleValue();
                    Document doc = documents.get(index);
                    doc.getMetadata().put("rerankScore", score);
                    return doc;
                })
                .toList();
    }
}
```

**Rerank 的作用**:
- 向量检索和关键词检索都是"粗排"，Rerank 是"精排"
- Cross-Encoder 模型会同时看 query 和 document，做更精细的相关性判断
- 但 Cross-Encoder 计算量大，所以只对 Top-K 候选做 Rerank

**学习检查点**:
- [ ] 理解 Bi-Encoder（向量检索）vs Cross-Encoder（Rerank）的区别
- [ ] 能说清 Parent 提升为什么能改善答案质量
- [ ] 知道 Rerank 为什么只处理 Top-K 而不是全部文档

---

### 第16章 Prompt 组装与答案生成

#### 16.1 Prompt 组装（证据预算管理）

```java
// RagPromptAssemblyService.java
@Service
public class RagPromptAssemblyService {

    /** 组装 System Prompt 和 User Prompt */
    public RagPromptAssemblyResult assemble(ConversationExecutionPlan plan,
                                             RagRetrievalContext context) {
        int totalBudget = plan.getPromptTokenBudget();  // 总 token 预算
        int systemReserve = 1500;                        // System Prompt 保留
        int evidenceBudget = totalBudget - systemReserve; // 证据可用预算

        // 按子问题分配预算
        int perSubQuestionBudget = evidenceBudget / Math.max(context.getSubQuestionCount(), 1);

        // 构建证据块
        StringBuilder evidenceBuilder = new StringBuilder();
        int usedBudget = 0;
        List<ReferenceView> renderedReferences = new ArrayList<>();
        List<ReferenceView> omittedReferences = new ArrayList<>();

        for (SubQuestionEvidence evidence : context.getSubQuestionEvidenceList()) {
            String sqHeader = "### 子问题 " + (evidence.getIndex() + 1) + ": "
                    + evidence.getSubQuestion() + "\n";
            evidenceBuilder.append(sqHeader);
            usedBudget += estimateTokens(sqHeader);

            for (ReferenceView ref : evidence.getReferences()) {
                String refText = formatReference(ref);
                int refTokens = estimateTokens(refText);

                if (usedBudget + refTokens > perSubQuestionBudget) {
                    omittedReferences.add(ref);  // 超预算，丢弃
                    continue;
                }
                evidenceBuilder.append(refText);
                usedBudget += refTokens;
                renderedReferences.add(ref);
            }
        }

        // 组装最终 Prompt
        String systemPrompt = buildSystemPrompt(plan);
        String userPrompt = buildUserPrompt(evidenceBuilder.toString(), plan);

        return new RagPromptAssemblyResult(
                systemPrompt, userPrompt,
                totalBudget, perSubQuestionBudget,
                renderedReferences, omittedReferences
        );
    }

    private String buildSystemPrompt(ConversationExecutionPlan plan) {
        return """
                你是一个专业的企业知识问答助手。请严格基于以下提供的证据回答问题。

                规则：
                1. 只使用提供的证据内容回答，不要编造
                2. 如果证据不足，请明确说明"根据现有资料无法确定"
                3. 回答时引用来源章节
                4. 使用中文回答
                """;
    }

    private String buildUserPrompt(String evidence, ConversationExecutionPlan plan) {
        return """
                ## 问题
                %s

                ## 证据
                %s

                请基于以上证据回答问题。
                """.formatted(plan.getQuestion(), evidence);
    }
}
```

#### 16.2 答案生成

```java
// RagChatExecutor.java 的 streamFromRetrievalContext 方法
private Flux<String> streamFromRetrievalContext(TaskInfo taskInfo,
                                                 ConversationExecutionPlan plan,
                                                 RagRetrievalContext context) {

    // 上下文为空 → 直接返回兜底回复
    if (context.isEmpty()) {
        return Flux.just(plan.getNoEvidenceReply());
    }

    // 组装 Prompt
    RagPromptAssemblyResult promptResult = ragPromptAssemblyService.assemble(plan, context);

    // 流式调用大模型
    return observedChatModelService.streamText(
            "rag_answer",
            promptResult.getSystemPrompt(),
            promptResult.getUserPrompt(),
            taskInfo.traceRecorder()
    );
}
```

#### 16.3 推荐问题生成

```java
// RecommendationService.java
public List<String> generateRecommendations(String question, String answer,
                                              List<ConversationExchangeView> recentExchanges,
                                              ConversationTraceRecorder traceRecorder) {
    if (!properties.isRecommendationEnabled()) return List.of();

    // 异步执行，带超时保护
    return CompletableFuture.supplyAsync(
            () -> generateRecommendationsInternal(question, answer, recentExchanges, traceRecorder),
            recommendationExecutorService
    )
    .orTimeout(properties.getRecommendationTimeoutMs(), TimeUnit.MILLISECONDS)
    .exceptionally(e -> {
        log.warn("推荐问题生成超时或失败: {}", e.getMessage());
        return List.of();
    })
    .join();
}

private List<String> generateRecommendationsInternal(...) {
    StringBuilder prompt = new StringBuilder(properties.getRecommendationPrompt());
    // 添加最近上下文
    // 添加当前问题和答案

    String content = observedChatModelService.callText("recommendation", null, prompt.toString(), traceRecorder);

    // 解析 JSON 数组
    String jsonArray = extractJsonArray(content);
    List<String> rawList = objectMapper.readValue(jsonArray, new TypeReference<>() {});

    // 去重，最多 3 个
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String item : rawList) {
        unique.add(item.trim());
        if (unique.size() >= 3) break;
    }
    return new ArrayList<>(unique);
}
```

**学习检查点**:
- [ ] 理解 Prompt 组装中 token 预算管理的意义
- [ ] 能说清证据不足时的兜底策略
- [ ] 知道推荐问题为什么用异步 + 超时

---

### 第17章 对话记忆管理

#### 17.1 多轮对话上下文

```java
// ConversationMemoryService.java
@Service
public class ConversationMemoryService {

    /** 获取最近 N 轮对话 */
    public List<ConversationExchangeView> getRecentHistory(String sessionId, int maxTurns) {
        return conversationMapper.selectRecentBySessionId(sessionId, maxTurns)
                .stream()
                .map(this::toExchangeView)
                .toList();
    }

    /** 保存一轮对话 */
    @Transactional
    public void saveExchange(String sessionId, String question, String answer,
                              List<ReferenceView> references) {
        // 保存用户消息
        conversationMapper.insert(Conversation.builder()
                .sessionId(sessionId)
                .role("user")
                .content(question)
                .build());

        // 保存助手回复（含引用来源）
        conversationMapper.insert(Conversation.builder()
                .sessionId(sessionId)
                .role("assistant")
                .content(answer)
                .referencesJson(toJson(references))
                .build());
    }
}
```

#### 17.2 会话摘要

长时间对话后，对历史做摘要以压缩上下文：

```java
public String generateSummary(String sessionId, List<ConversationExchangeView> history) {
    if (history.size() <= properties.getHistoryPreviewTurns()) {
        return "";  // 历史太短，不需要摘要
    }

    String prompt = "请对以下对话进行简要摘要，保留关键信息：\n"
            + formatHistory(history);

    return chatModelService.callText("summary", null, prompt, null);
}
```

**学习检查点**:
- [ ] 理解对话记忆在多轮问答中的作用
- [ ] 知道为什么需要会话摘要（上下文窗口限制）
- [ ] 能说清 `historyPreviewTurns` 参数的作用

---

### 第18章 可观测性与链路追踪

#### 18.1 追踪记录器

```java
// ConversationTraceRecorder.java
@Component
public class ConversationTraceRecorder {

    /** 开始一个阶段 */
    public StageHandle startStage(ConversationTraceStageCode code, String mode,
                                   String message, Map<String, Object> detail) {
        StageHandle handle = new StageHandle(code, System.currentTimeMillis());
        // 记录阶段开始事件
        traceStore.save(TraceEvent.builder()
                .stageCode(code)
                .mode(mode)
                .message(message)
                .detail(detail)
                .timestamp(System.currentTimeMillis())
                .type("START")
                .build());
        return handle;
    }

    /** 完成阶段 */
    public void completeStage(StageHandle handle, String message,
                               Map<String, Object> detail) {
        long durationMs = System.currentTimeMillis() - handle.startTime();
        traceStore.save(TraceEvent.builder()
                .stageCode(handle.code())
                .message(message)
                .durationMs(durationMs)
                .detail(detail)
                .type("COMPLETE")
                .build());
    }

    /** 阶段失败 */
    public void failStage(StageHandle handle, String message, String error,
                           Map<String, Object> detail) {
        traceStore.save(TraceEvent.builder()
                .stageCode(handle.code())
                .message(message)
                .error(error)
                .type("FAIL")
                .build());
    }
}
```

#### 18.2 追踪阶段枚举

```java
public enum ConversationTraceStageCode {
    QUERY_REWRITE,          // 查询改写
    RAG_RETRIEVE,           // RAG 检索
    EVIDENCE_BUDGET,        // 证据预算
    ANSWER_GENERATE,        // 答案生成
    RECOMMENDATION,         // 推荐问题
    SESSION_SUMMARY         // 会话摘要
}
```

#### 18.3 调试信息收集

```java
// TaskInfo 中的 debugTrace
taskInfo.debugTrace().setRagSystemPrompt(systemPrompt);
taskInfo.debugTrace().setRagUserPrompt(userPrompt);
taskInfo.debugTrace().setRetrievalNotes(retrievalNotes);
taskInfo.debugTrace().setUsedChannels(usedChannels);

// 可通过 /api/chat/detail 接口查看完整调试信息
@GetMapping("/detail/{sessionId}")
public ApiResponse<ChatDebugDetail> getDebugDetail(@PathVariable String sessionId) {
    return ApiResponse.success(chatService.getDebugDetail(sessionId));
}
```

**学习检查点**:
- [ ] 理解链路追踪在 RAG 系统中的调试价值
- [ ] 能说出每个 TraceStageCode 代表的阶段
- [ ] 知道如何通过 debugTrace 定位检索质量问题

---

## 阶段四：分布式基础设施（第4周）

### 第19章 雪花算法分布式ID

#### 19.1 ID 结构

```
 0 | 00000000000000000000000000000000000000001 | 0000000000 | 000000000000
符号位(1bit)   |   时间戳(41bit)           | 机器ID(10bit) | 序列号(12bit)
```

- **时间戳**: 41bit，可表示约 69 年（从自定义纪元开始）
- **机器ID**: 10bit，最多 1024 个节点
- **序列号**: 12bit，同一毫秒最多 4096 个 ID

#### 19.2 实现代码

```java
// SnowflakeIdGenerator.java
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1700000000000L;  // 自定义纪元
    private static final long WORKER_BITS = 10;
    private static final long SEQUENCE_BITS = 12;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);    // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);   // 4095

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(RedissonClient redissonClient) {
        // 通过 Redis Lua 脚本获取 WorkerID，保证分布式唯一
        this.workerId = acquireWorkerId(redissonClient);
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成ID");
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号用尽，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        // 拼接 ID
        return ((timestamp - EPOCH) << (WORKER_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
```

#### 19.3 Redis Lua 分配 WorkerID

```java
private long acquireWorkerId(RedissonClient redissonClient) {
    RScript script = redissonClient.getScript();

    // Lua 脚本：原子递增并取模，保证 WorkerID 唯一
    String luaScript = """
            local key = KEYS[1]
            local maxWorkerId = tonumber(ARGV[1])
            local id = redis.call('INCR', key)
            return (id - 1) % maxWorkerId
            """;

    Long workerId = script.eval(RScript.Mode.READ_WRITE,
            luaScript, RScript.ReturnType.INTEGER,
            List.of("snowflake:worker:id"),  // Redis key
            MAX_WORKER_ID                     // 最大 WorkerID
    );

    return workerId == null ? 0L : workerId;
}
```

**学习检查点**:
- [ ] 能手动推算雪花 ID 的时间戳、机器ID、序列号
- [ ] 理解为什么用 Redis Lua 而不是数据库自增
- [ ] 知道时钟回拨问题的处理方式

---

### 第20章 Redisson 分布式工具

#### 20.1 Redis Lease 会话锁

防止同一会话并发执行（避免重复问答）：

```java
// RedisLeaseManager.java
@Service
public class RedisLeaseManager {

    private final RedissonClient redissonClient;

    /** 获取租约，TTL=30秒 */
    public boolean acquire(String sessionId) {
        String key = "chat:lease:" + sessionId;
        RAtomicLong lease = redissonClient.getAtomicLong(key);

        // 原子设置，如果 key 不存在则设置成功
        boolean acquired = lease.compareAndSet(0, System.currentTimeMillis() + 30_000);
        if (acquired) {
            // 启动续租线程，每 10 秒续一次
            startRenewal(sessionId);
        }
        return acquired;
    }

    /** 续租：延长 TTL */
    public void renew(String sessionId) {
        String key = "chat:lease:" + sessionId;
        redissonClient.getAtomicLong(key)
                .set(System.currentTimeMillis() + 30_000);
    }

    /** 释放租约 */
    public void release(String sessionId) {
        String key = "chat:lease:" + sessionId;
        redissonClient.getAtomicLong(key).set(0);
        stopRenewal(sessionId);
    }

    private void startRenewal(String sessionId) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                () -> renew(sessionId),
                10, 10, TimeUnit.SECONDS  // 每 10 秒续一次
        );
        renewalMap.put(sessionId, scheduler);
    }
}
```

#### 20.2 分布式锁

```java
// 使用 Redisson 分布式锁
RLock lock = redissonClient.getLock("lock:document:" + documentId);
try {
    // 尝试加锁，最多等 5 秒，锁持有 30 秒自动释放
    boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
    if (!acquired) {
        throw new BusinessException("文档正在处理中，请稍后重试");
    }
    // 临界区操作
    processDocument(documentId);
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

#### 20.3 延迟队列

```java
// RDelayedQueue 延迟处理
RBlockingQueue<TaskMessage> blockingQueue = redissonClient.getBlockingQueue("task:queue");
RDelayedQueue<TaskMessage> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

// 30 秒后投递
delayedQueue.offer(new TaskMessage(documentId), 30, TimeUnit.SECONDS);

// 消费端
@Scheduled(fixedDelay = 1000)
public void consumeDelayedTask() {
    TaskMessage task = blockingQueue.poll();
    if (task != null) {
        processTask(task);
    }
}
```

**学习检查点**:
- [ ] 理解 Lease 和 Lock 的区别（Lease 有自动过期，Lock 需要手动释放）
- [ ] 知道为什么要定期续租（防止处理过程中租约过期）
- [ ] 能说清延迟队列的应用场景

---

### 第21章 Spring AI 集成

#### 21.1 ChatModel 配置（DeepSeek）

```java
@Configuration
public class AiModelConfig {

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .openAiChatOptions(OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.7)
                        .build())
                .restClientBuilder(/* ... */)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new QwenEmbeddingModel(/* Qwen3 Embedding API 配置 */);
    }
}
```

#### 21.2 ObservedChatModelService 封装

```java
// ObservedChatModelService.java
@Service
public class ObservedChatModelService {

    private final Map<String, ChatModel> chatModels;

    /** 同步调用，返回完整文本 */
    public String callText(String modelKey, String systemPrompt,
                            String userPrompt, ConversationTraceRecorder recorder) {
        ChatModel model = chatModels.get(modelKey);
        if (model == null) {
            throw new IllegalArgumentException("未找到模型: " + modelKey);
        }

        List<Message> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userPrompt));

        long startTime = System.currentTimeMillis();
        ChatResponse response = model.call(new Prompt(messages));
        long durationMs = System.currentTimeMillis() - startTime;

        // 记录调用指标
        recordMetrics(modelKey, durationMs, response);

        return response.getResult().getOutput().getContent();
    }

    /** 流式调用，返回 Flux<String> */
    public Flux<String> streamText(String modelKey, String systemPrompt,
                                    String userPrompt, ConversationTraceRecorder recorder) {
        ChatModel model = chatModels.get(modelKey);

        List<Message> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userPrompt));

        return model.stream(new Prompt(messages))
                .map(response -> {
                    String content = response.getResult().getOutput().getContent();
                    // 记录首 token 响应时间
                    recordFirstTokenIfNeeded(recorder);
                    return content;
                });
    }
}
```

#### 21.3 React Agent（工具调用）

```java
// 使用 Spring AI 的 React Agent 模式
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultTools(documentSearchTool, calculatorTool, webSearchTool)
        .build();

// Agent 自动决定调用哪个工具
String answer = chatClient.prompt()
        .user("帮我搜索关于 RAG 的文档，并计算相关性分数")
        .call()
        .content();
```

**学习检查点**:
- [ ] 理解 Spring AI 的 ChatModel 抽象层
- [ ] 知道 callText vs streamText 的使用场景
- [ ] 了解 React Agent 的工具调用原理

---

### 第22章 面试准备与高频问题

#### 22.1 项目亮点速记

| 亮点 | 一句话描述 |
|------|-----------|
| Parent-Child 分块 | Child 用于精确检索，Parent 用于完整上下文 |
| 4 种分块策略 | 按文档类型自动选择最优分块方式 |
| 双通道混合检索 | 向量（语义）+ 关键词（精确），RRF 融合 |
| 保守拆分策略 | LLM 建议拆分但代码校验，避免过度拆分 |
| Evidence Gate | 检索结果门控，过滤低质量候选 |
| Rerank 精排 | Cross-Encoder 对 Top-K 做精细化排序 |
| Redis Lease | TTL=30s + 10s 续租，防止并发执行 |
| 雪花 ID | Redis Lua 分配 WorkerID，解决分布式 ID |
| SSE 流式 | Reactor Flux 实现逐 token 推送 |

#### 22.2 高频面试问答

**Q: 为什么用双通道检索而不是只用向量检索？**
> 向量检索擅长语义匹配（"如何提高效率"能匹配到"优化性能"），但对精确关键词（如型号、编号）不敏感。关键词检索（BM25）恰好相反。两者互补，用 RRF 融合取长补短。

**Q: RRF 公式中 k=60 的作用是什么？**
> k 是一个平滑常数。如果没有 k（即直接用 1/rank），排名第一和第二的得分差异是 1 - 0.5 = 0.5，差异过大。加入 k=60 后，差异是 1/61 - 1/62 ≈ 0.00026，显著降低了排名差异的放大效应，让多个通道的得分更公平地融合。

**Q: Parent-Child 分块为什么能改善答案质量？**
> 小块检索精准但上下文不完整（可能只有半句话），大块上下文完整但检索时容易包含无关内容。Parent-Child 结合两者优势：用小块精确检索，命中后提升到父块获取完整段落，确保大模型有足够的上下文生成答案。

**Q: 为什么采用保守拆分策略？**
> LLM 有时会过度拆分简单问题（如把"怎么注册"拆成多个子问题），导致检索分散、答案碎片化。保守策略只在用户明确使用了 `？？`、`分别`、`第一...第二` 等标记时才拆分，宁可检索范围大一些，也不要拆得太碎。

**Q: Redis Lease 和 Redis Lock 有什么区别？**
> Lock 是互斥锁，防止并发操作。Lease 是租约机制，有自动过期时间（TTL=30s），需要定期续租（每 10s）。如果持有者崩溃，租约会在 30s 后自动释放，避免死锁。本项目用 Lease 而非 Lock，因为问答操作可能执行很长时间，需要租约机制保证安全性。

**Q: 4 种分块策略分别适合什么场景？**
> - STRUCTURE：有明确标题结构的文档（技术手册、规范文档）
> - RECURSIVE：结构不明确的普通文档，通用兜底策略
> - SEMANTIC：内容语义跨度大的文档（新闻、报告）
> - LLM：高质量文档，能精准判断语义边界，但成本高、速度慢

**Q: Evidence Gate 为什么需要？**
> 如果不过滤，低相似度的检索结果也会进入后续流程，稀释高质量证据的权重，导致大模型生成不准确的答案。门控过滤掉相似度 < 0.5 的向量结果和得分 < 最高分 30% 的关键词结果，保证证据质量。

**Q: Snowflake ID 为什么需要 Redis Lua 分配 WorkerID？**
> 雪花算法要求每个节点有唯一的 WorkerID。如果用配置文件硬编码，运维成本高且容易冲突。用 Redis Lua 脚本原子递增取模，自动分配 WorkerID，新节点上线无需人工配置。

#### 22.3 4 周学习计划总结

| 周次 | 学习内容 | 每日任务 |
|------|---------|---------|
| 第1周 | 项目骨架、Maven 模块、公共模块、数据库设计、Spring Boot 配置 | 每天 2-3 小时，通读代码 + 画架构图 |
| 第2周 | 文档处理流水线：上传→MinIO→Kafka→Tika→分块→向量化→索引 | 每天 2-3 小时，读源码 + 本地调试 |
| 第3周 | 问答流水线：SSE→改写→路由→检索→RRF→Rerank→答案生成 | 每天 2-3 小时，重点理解 RAG 核心算法 |
| 第4周 | 分布式工具：雪花ID、Redisson、Spring AI、面试准备 | 每天 2-3 小时，模拟面试 + 查漏补缺 |

#### 22.4 面试前自检清单

- [ ] 能在白板上画出文档上传到索引的完整流程
- [ ] 能在白板上画出用户提问到返回答案的完整流程
- [ ] 能说清 RRF 公式并手动计算一个例子
- [ ] 能解释 Parent-Child 分块的设计动机
- [ ] 能对比 4 种分块策略的优缺点
- [ ] 能说清 Evidence Gate 的过滤规则
- [ ] 能解释 Rerank 的原理和必要性
- [ ] 能描述 Redis Lease 的续租机制
- [ ] 能解释雪花 ID 的位分配和 Redis Lua 分配方案
- [ ] 能说清 SSE 流式响应的实现原理

---

## 附录

### 附录A：核心代码文件速查表

| 模块 | 功能 | 文件路径 |
|------|------|---------|
| manage | 文档上传 | `.../manage/controller/DocumentManageController.java` |
| manage | MinIO 存储 | `.../manage/service/impl/MinioDocumentStorageService.java` |
| manage | Kafka 生产者 | `.../manage/mq/DocumentKafkaProducer.java` |
| manage | Kafka 消费者 | `.../manage/mq/DocumentParseConsumer.java` |
| manage | Tika 解析 | `.../manage/service/impl/TikaDocumentParserService.java` |
| manage | 分块策略 | `.../manage/service/impl/DocumentStrategyServiceImpl.java` |
| manage | 向量化 | `.../manage/service/impl/DefaultDocumentVectorGateway.java` |
| chat | SSE 控制器 | `.../chatagent/controller/BusinessChatController.java` |
| chat | 会话服务 | `.../chatagent/service/BusinessChatService.java` |
| chat | 查询改写 | `.../chatagent/rag/service/ChatQueryRewriteService.java` |
| chat | 文档路由 | `.../chatagent/rag/service/DocumentQuestionRouter.java` |
| chat | 检索引擎 | `.../chatagent/rag/service/RagRetrievalEngine.java` |
| chat | Rerank | `.../chatagent/rag/service/HttpDocumentRerankPostProcessor.java` |
| chat | 执行器 | `.../chatagent/rag/executor/RagChatExecutor.java` |
| chat | 推荐服务 | `.../chatagent/service/RecommendationService.java` |

### 附录B：核心参数速查表

| 参数 | 默认值 | 说明 |
|------|-------|------|
| Parent 最大字符数 | 2200 | 父块大小限制 |
| Parent 重叠字符数 | 180 | 父块间重叠 |
| Child 最大字符数 | 480 | 子块大小限制 |
| RRF k 值 | 60 | RRF 公式平滑常数 |
| candidateTopK | 80 | RRF 后候选池大小 |
| finalTopK | 10 | 最终返回文档数 |
| minVectorSimilarity | 0.5 | 向量相似度最低阈值 |
| keywordRelativeFloor | 0.3 | 关键词得分相对阈值 |
| Rerank topN | 10 | Rerank 返回数 |
| Lease TTL | 30s | 会话租约有效期 |
| Lease renew interval | 10s | 续租间隔 |
| Embedding batch size | 10 | 向量化批大小 |
| Embedding dimensions | 1024 | Qwen3 Embedding 维度 |

