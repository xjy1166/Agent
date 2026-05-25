package org.javaup.ai.manage.service.impl;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.manage.config.DocumentManageProperties;
import org.javaup.ai.manage.data.SuperAgentDocument;
import org.javaup.ai.manage.data.SuperAgentDocumentChunk;
import org.javaup.ai.manage.data.SuperAgentDocumentParentBlock;
import org.javaup.ai.manage.data.SuperAgentDocumentStrategyPlan;
import org.javaup.ai.manage.data.SuperAgentDocumentStrategyStep;
import org.javaup.ai.manage.data.SuperAgentDocumentStructureNode;
import org.javaup.ai.manage.data.SuperAgentDocumentTask;
import org.javaup.ai.manage.mapper.SuperAgentDocumentChunkMapper;
import org.javaup.ai.manage.mapper.SuperAgentDocumentMapper;
import org.javaup.ai.manage.mapper.SuperAgentDocumentParentBlockMapper;
import org.javaup.ai.manage.mapper.SuperAgentDocumentStrategyPlanMapper;
import org.javaup.ai.manage.mapper.SuperAgentDocumentStrategyStepMapper;
import org.javaup.ai.manage.mapper.SuperAgentDocumentTaskMapper;
import org.javaup.ai.manage.service.DocumentAsyncProcessService;
import org.javaup.ai.manage.service.DocumentParserService;
import org.javaup.ai.manage.service.DocumentProfileService;
import org.javaup.ai.manage.service.DocumentStorageService;
import org.javaup.ai.manage.service.DocumentNavigationIndexService;
import org.javaup.ai.manage.service.DocumentStructureGraphProjectionService;
import org.javaup.ai.manage.service.DocumentStructureNodeService;
import org.javaup.ai.manage.service.DocumentStrategyService;
import org.javaup.ai.manage.service.DocumentTaskLogService;
import org.javaup.ai.manage.service.DocumentVectorGateway;
import org.javaup.ai.manage.service.keyword.DocumentKeywordSearchGateway;
import org.javaup.ai.manage.support.ChunkCandidate;
import org.javaup.ai.manage.support.DocumentAnalysisResult;
import org.javaup.ai.manage.support.ParentBlockCandidate;
import org.javaup.ai.manage.support.DocumentStrategyPlanDraft;
import org.javaup.ai.manage.support.DocumentStrategyStepDraft;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentChunkSourceTypeEnum;
import org.javaup.enums.DocumentFileTypeEnum;
import org.javaup.enums.DocumentIndexStatusEnum;
import org.javaup.enums.DocumentLogLevelEnum;
import org.javaup.enums.DocumentOperatorTypeEnum;
import org.javaup.enums.DocumentParseStatusEnum;
import org.javaup.enums.DocumentPlanSourceEnum;
import org.javaup.enums.DocumentPlanStatusEnum;
import org.javaup.enums.DocumentStrategyExecuteStatusEnum;
import org.javaup.enums.DocumentStrategyPipelineTypeEnum;
import org.javaup.enums.DocumentStrategyStatusEnum;
import org.javaup.enums.DocumentTaskEventTypeEnum;
import org.javaup.enums.DocumentTaskStageEnum;
import org.javaup.enums.DocumentTaskStatusEnum;
import org.javaup.enums.DocumentVectorStatusEnum;
import org.javaup.enums.DocumentVectorStoreTypeEnum;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**

 * @description: 服务实现层
 
 **/

@Slf4j
@Service
public class DocumentAsyncProcessServiceImpl implements DocumentAsyncProcessService {

    private final SuperAgentDocumentMapper documentMapper;

    private final SuperAgentDocumentStrategyPlanMapper planMapper;

    private final SuperAgentDocumentStrategyStepMapper stepMapper;

    private final SuperAgentDocumentTaskMapper taskMapper;

    private final SuperAgentDocumentParentBlockMapper parentBlockMapper;

    private final SuperAgentDocumentChunkMapper chunkMapper;

    private final DocumentStorageService storageService;

    private final DocumentParserService parserService;

    private final DocumentStrategyService strategyService;

    private final DocumentStructureNodeService structureNodeService;

    private final DocumentTaskLogService taskLogService;

    private final DocumentVectorGateway vectorGateway;

    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    private final DocumentManageProperties properties;

    private final DocumentProfileService documentProfileService;

    @Resource
    private UidGenerator uidGenerator;

    public DocumentAsyncProcessServiceImpl(SuperAgentDocumentMapper documentMapper,
                                           SuperAgentDocumentStrategyPlanMapper planMapper,
                                           SuperAgentDocumentStrategyStepMapper stepMapper,
                                           SuperAgentDocumentTaskMapper taskMapper,
                                           SuperAgentDocumentParentBlockMapper parentBlockMapper,
                                           SuperAgentDocumentChunkMapper chunkMapper,
                                           DocumentStorageService storageService,
                                           DocumentParserService parserService,
                                           DocumentStrategyService strategyService,
                                           DocumentStructureNodeService structureNodeService,
                                           DocumentTaskLogService taskLogService,
                                           DocumentVectorGateway vectorGateway,
                                           ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider,
                                           ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider,
                                           ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider,
                                           DocumentManageProperties properties,
                                           DocumentProfileService documentProfileService) {
        this.documentMapper = documentMapper;
        this.planMapper = planMapper;
        this.stepMapper = stepMapper;
        this.taskMapper = taskMapper;
        this.parentBlockMapper = parentBlockMapper;
        this.chunkMapper = chunkMapper;
        this.storageService = storageService;
        this.parserService = parserService;
        this.strategyService = strategyService;
        this.structureNodeService = structureNodeService;
        this.taskLogService = taskLogService;
        this.vectorGateway = vectorGateway;
        this.keywordSearchGatewayProvider = keywordSearchGatewayProvider;
        this.navigationIndexServiceProvider = navigationIndexServiceProvider;
        this.graphProjectionServiceProvider = graphProjectionServiceProvider;
        this.properties = properties;
        this.documentProfileService = documentProfileService;
    }

    @Override

    public void handleParseRoute(Long documentId, Long taskId) {

        //查询文档和任务记录
        SuperAgentDocument document = documentMapper.selectById(documentId);
        SuperAgentDocumentTask task = taskMapper.selectById(taskId);
        if (document == null || task == null) {
            log.warn("解析任务对应的文档或任务不存在，documentId={}, taskId={}", documentId, taskId);
            return;
        }

        Date startTime = new Date();
        try {

            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            task.setStartTime(startTime);
            taskMapper.updateById(task);

            document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
            documentMapper.updateById(document);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始解析文档内容。",
                Map.of("objectName", document.getObjectName()));

            //解析文件
            byte[] fileBytes = storageService.downloadObject(document.getObjectName());
            DocumentAnalysisResult analysisResult = parserService.parse(fileBytes, document.getOriginalFileName(),
                document.getMimeType(), DocumentFileTypeEnum.getRc(document.getFileType()));

            // 5. 上传解析后的纯文本到 MinIO
            String parseTextPath = storageService.uploadParsedText(documentId, analysisResult.getParsedText());

            // 6. 构建文档结构树
            List<SuperAgentDocumentStructureNode> structureNodes = structureNodeService.replaceDocumentNodes(
                documentId,
                taskId,
                analysisResult.getStructureNodes()
            );
            int structureNodeCount = structureNodes.size();
            // 7. 同步导航索引到 Elasticsearch
            syncNavigationArtifacts(documentId, taskId, structureNodes);
            // 8. AI 生成文档画像（摘要、主题等）
            documentProfileService.generateProfile(documentId, analysisResult, structureNodes);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析完成。",
                Map.of(
                    "charCount", analysisResult.getCharCount(),
                    "tokenCount", analysisResult.getTokenCount(),
                    "structureLevel", analysisResult.getStructureLevel(),
                    "contentQualityLevel", analysisResult.getContentQualityLevel(),
                    "structureNodeCount", structureNodeCount
                ));

            task.setCurrentStage(DocumentTaskStageEnum.STRATEGY_ROUTE.getCode());
            taskMapper.updateById(task);

            // 9. AI 推荐切块策略
            DocumentStrategyPlanDraft planDraft = strategyService.recommendStrategy(document, analysisResult);
            Long planId = uidGenerator.getUid();
            int planVersion = getNextPlanVersion(documentId);

            SuperAgentDocumentStrategyPlan plan = new SuperAgentDocumentStrategyPlan();
            plan.setId(planId);
            plan.setDocumentId(documentId);
            plan.setPlanVersion(planVersion);
            plan.setPlanSource(DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode());
            plan.setPlanStatus(DocumentPlanStatusEnum.WAIT_CONFIRM.getCode());
            plan.setStrategyCount(planDraft.getParentSteps().size() + planDraft.getChildSteps().size());
            plan.setStrategySnapshot(planDraft.getStrategySnapshot());
            plan.setRecommendReason(planDraft.getRecommendReason());
            plan.setStatus(BusinessStatus.YES.getCode());
            planMapper.insert(plan);

            // 10. 保存策略方案到数据库
            for (int index = 0; index < planDraft.getParentSteps().size(); index++) {
                DocumentStrategyStepDraft draft = planDraft.getParentSteps().get(index);
                SuperAgentDocumentStrategyStep step = new SuperAgentDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }
            for (int index = 0; index < planDraft.getChildSteps().size(); index++) {
                DocumentStrategyStepDraft draft = planDraft.getChildSteps().get(index);
                SuperAgentDocumentStrategyStep step = new SuperAgentDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            document.setParseStatus(DocumentParseStatusEnum.PARSE_SUCCESS.getCode());
            document.setStrategyStatus(DocumentStrategyStatusEnum.RECOMMENDED.getCode());
            document.setCharCount(analysisResult.getCharCount());
            document.setTokenCount(analysisResult.getTokenCount());
            document.setStructureLevel(analysisResult.getStructureLevel());
            document.setContentQualityLevel(analysisResult.getContentQualityLevel());
            document.setParseTextPath(parseTextPath);
            document.setParseErrorMsg(null);
            document.setCurrentPlanId(planId);
            document.setLastParseTaskId(taskId);
            document.setStructureNodeCount(structureNodeCount);
            documentMapper.updateById(document);

            finishTaskSuccess(task, DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(), startTime);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(),
                DocumentTaskEventTypeEnum.RECOMMEND_STRATEGY.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "系统已生成推荐策略。",
                detail("planId", planId,
                    "strategySnapshot", planDraft.getStrategySnapshot(),
                    "parentStepCount", planDraft.getParentSteps().size(),
                    "childStepCount", planDraft.getChildSteps().size(),
                    "structureNodeCount", structureNodeCount,
                    "recommendReason", planDraft.getRecommendReason()));
        }
        catch (Exception exception) {
            log.error("异步解析文档失败，documentId={}, taskId={}", documentId, taskId, exception);

            document.setParseStatus(DocumentParseStatusEnum.PARSE_FAILED.getCode());
            document.setParseErrorMsg(exception.getMessage());
            documentMapper.updateById(document);

            failTask(task, startTime, exception, DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析失败。",
                detail("error", exception.getMessage()));
        }
    }

    @Override
    public void handleIndexBuild(Long documentId, Long taskId, Long planId) {

        // 【1. 数据校验】检查文档、任务、策略方案是否存在，防止空指针
        SuperAgentDocument document = documentMapper.selectById(documentId);
        SuperAgentDocumentTask task = taskMapper.selectById(taskId);
        SuperAgentDocumentStrategyPlan plan = planMapper.selectById(planId);
        if (document == null || task == null || plan == null) {
            log.warn("索引任务对应的数据不存在，documentId={}, taskId={}, planId={}", documentId, taskId, planId);
            return;
        }

        Date startTime = new Date();

        // 【2. 获取策略步骤】读取之前确认的切块流水线（如：先递归分块，再语义微调）
        List<SuperAgentDocumentStrategyStep> stepList = listSteps(planId);
        try {

            // 【3. 状态初始化】将任务和文档状态更新为“运行中/构建中”
            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
            task.setStartTime(startTime);
            taskMapper.updateById(task);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
            documentMapper.updateById(document);

            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTING.getCode());

            // 【4. 记录开始日志】
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行切块流水线。",
                Map.of("strategySnapshot", plan.getStrategySnapshot()));

            // 【5. 获取原文】从 MinIO 下载解析后的纯文本
            String parsedText = storageService.downloadText(document.getParseTextPath());

            // 【6. 核心切块】根据策略将长文本拆解为“父块-子块”结构
            List<ParentBlockCandidate> parentBlockCandidateList = strategyService.buildParentBlocks(document, plan, stepList, parsedText);

            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_SUCCESS.getCode());

            // 【7. 记录切块完成日志】
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块执行完成。",
                Map.of(
                    "parentCount", parentBlockCandidateList.size(),
                    "childCount", countChildCandidates(parentBlockCandidateList)
                ));

            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode());
            taskMapper.updateById(task);

            // 【8. 数据清洗】过滤掉内容为空或没有有效子块的无效父块
            List<ParentBlockCandidate> finalParentBlockList = parentBlockCandidateList.stream()
                .filter(item -> item != null
                    && StrUtil.isNotBlank(item.getText())
                    && item.getChildChunks() != null
                    && item.getChildChunks().stream().anyMatch(child -> StrUtil.isNotBlank(child.getText())))
                .toList();

            // 【9. 记录后处理日志】
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块后处理完成。",
                Map.of(
                    "parentCount", finalParentBlockList.size(),
                    "childCount", countChildCandidates(finalParentBlockList)
                ));

            // 【10. 实体转换】将内存中的候选对象转换为数据库实体对象
            ParentChildEntityBundle entityBundle = buildParentChildEntities(documentId, taskId, planId, finalParentBlockList);
            List<SuperAgentDocumentParentBlock> parentBlockEntityList = entityBundle.parentBlocks();
            List<SuperAgentDocumentChunk> chunkEntityList = entityBundle.childChunks();

            // 【11. 持久化入库】将父块和子块存入 MySQL
            for (SuperAgentDocumentParentBlock parentBlock : parentBlockEntityList) {
                parentBlockMapper.insert(parentBlock);
            }
            for (SuperAgentDocumentChunk chunk : chunkEntityList) {
                chunkMapper.insert(chunk);
            }

            task.setCurrentStage(DocumentTaskStageEnum.VECTORIZE.getCode());
            taskMapper.updateById(task);

            // 【12. 记录向量化开始日志】
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行向量化。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.PG_VECTOR.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            // 【13. 向量计算与存储】调用 Embedding 模型计算向量并存入 PGVector
            vectorGateway.vectorize(chunkEntityList);

            // 【14. 关键词索引】如果启用了 ES，则建立倒排索引
            DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
            if (keywordSearchGateway != null) {
                keywordSearchGateway.indexChunks(chunkEntityList);
            }

            // 【15. 更新 Chunk 状态】标记向量化成功
            for (SuperAgentDocumentChunk chunk : chunkEntityList) {
                chunkMapper.updateById(chunk);
            }

            // 【16. 记录向量化完成日志】
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "向量化完成。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / DefaultDocumentVectorGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.PG_VECTOR.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            task.setCurrentStage(DocumentTaskStageEnum.STORE_COMPLETE.getCode());
            taskMapper.updateById(task);

            // 【17. 最终收尾】更新策略、文档和任务的最终成功状态
            plan.setPlanStatus(DocumentPlanStatusEnum.EXECUTED.getCode());
            planMapper.updateById(plan);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_SUCCESS.getCode());
            document.setLastIndexTaskId(taskId);
            documentMapper.updateById(document);

            finishTaskSuccess(task, DocumentTaskStageEnum.STORE_COMPLETE.getCode(), startTime);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STORE_COMPLETE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建完成。",
                Map.of("taskId", taskId, "chunkCount", chunkEntityList.size(), "parentCount", parentBlockEntityList.size()));
        }
        catch (Exception exception) {
            // 【18. 异常处理】捕获错误，回滚状态并记录失败原因
            log.error("异步构建索引失败，documentId={}, taskId={}, planId={}", documentId, taskId, planId, exception);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_FAILED.getCode());
            documentMapper.updateById(document);

            chunkMapper.update(null, new LambdaUpdateWrapper<SuperAgentDocumentChunk>()
                .eq(SuperAgentDocumentChunk::getTaskId, taskId)
                .eq(SuperAgentDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .set(SuperAgentDocumentChunk::getVectorStatus, DocumentVectorStatusEnum.VECTOR_FAILED.getCode())
                .set(SuperAgentDocumentChunk::getVectorStoreType, DocumentVectorStoreTypeEnum.PG_VECTOR.getCode()));

            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_FAILED.getCode());
            failTask(task, startTime, exception, task.getCurrentStage());
            taskLogService.saveLog(taskId, documentId,
                task.getCurrentStage(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建失败。",
                detail("error", exception.getMessage()));
        }
    }

    private ParentChildEntityBundle buildParentChildEntities(Long documentId,
                                                             Long taskId,
                                                             Long planId,
                                                             List<ParentBlockCandidate> parentBlockCandidateList) {
        List<SuperAgentDocumentParentBlock> parentBlockEntityList = new java.util.ArrayList<>();
        List<SuperAgentDocumentChunk> chunkEntityList = new java.util.ArrayList<>();
        int globalChunkNo = 1;

        for (int parentIndex = 0; parentIndex < parentBlockCandidateList.size(); parentIndex++) {
            ParentBlockCandidate parentCandidate = parentBlockCandidateList.get(parentIndex);
            if (parentCandidate == null || StrUtil.isBlank(parentCandidate.getText())) {
                continue;
            }

            SuperAgentDocumentParentBlock parentBlock = new SuperAgentDocumentParentBlock();
            parentBlock.setId(uidGenerator.getUid());
            parentBlock.setDocumentId(documentId);
            parentBlock.setTaskId(taskId);
            parentBlock.setPlanId(planId);
            parentBlock.setParentNo(parentIndex + 1);
            parentBlock.setSourceType(parentCandidate.getSourceType() == null
                ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : parentCandidate.getSourceType());
            parentBlock.setSectionPath(parentCandidate.getSectionPath());
            parentBlock.setStructureNodeId(parentCandidate.getStructureNodeId());
            parentBlock.setStructureNodeType(parentCandidate.getStructureNodeType());
            parentBlock.setCanonicalPath(parentCandidate.getCanonicalPath());
            parentBlock.setItemIndex(parentCandidate.getItemIndex());
            parentBlock.setParentText(parentCandidate.getText().trim());
            parentBlock.setCharCount(parentCandidate.getText().length());
            parentBlock.setTokenCount(estimateTokenCount(parentCandidate.getText()));
            parentBlock.setStatus(BusinessStatus.YES.getCode());

            int startChunkNo = globalChunkNo;
            int childCount = 0;
            for (ChunkCandidate childCandidate : parentCandidate.getChildChunks()) {
                if (childCandidate == null || StrUtil.isBlank(childCandidate.getText())) {
                    continue;
                }
                SuperAgentDocumentChunk chunk = new SuperAgentDocumentChunk();
                chunk.setId(uidGenerator.getUid());
                chunk.setDocumentId(documentId);
                chunk.setTaskId(taskId);
                chunk.setPlanId(planId);
                chunk.setParentBlockId(parentBlock.getId());
                chunk.setChunkNo(globalChunkNo++);
                chunk.setSourceType(childCandidate.getSourceType() == null
                    ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : childCandidate.getSourceType());
                chunk.setSectionPath(StrUtil.blankToDefault(childCandidate.getSectionPath(), parentCandidate.getSectionPath()));
                chunk.setStructureNodeId(childCandidate.getStructureNodeId());
                chunk.setStructureNodeType(childCandidate.getStructureNodeType());
                chunk.setCanonicalPath(childCandidate.getCanonicalPath());
                chunk.setItemIndex(childCandidate.getItemIndex());
                chunk.setChunkText(childCandidate.getText().trim());
                chunk.setCharCount(childCandidate.getText().length());

                chunk.setTokenCount(estimateTokenCount(childCandidate.getText()));
                chunk.setVectorStatus(DocumentVectorStatusEnum.WAIT_VECTOR.getCode());
                chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.PG_VECTOR.getCode());
                chunk.setStatus(BusinessStatus.YES.getCode());
                chunkEntityList.add(chunk);
                childCount++;
            }

            parentBlock.setChildCount(childCount);
            parentBlock.setStartChunkNo(childCount == 0 ? null : startChunkNo);
            parentBlock.setEndChunkNo(childCount == 0 ? null : globalChunkNo - 1);
            parentBlockEntityList.add(parentBlock);
        }

        return new ParentChildEntityBundle(parentBlockEntityList, chunkEntityList);
    }

    private int countChildCandidates(List<ParentBlockCandidate> parentBlockCandidateList) {
        if (parentBlockCandidateList == null || parentBlockCandidateList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ParentBlockCandidate candidate : parentBlockCandidateList) {
            if (candidate == null || candidate.getChildChunks() == null) {
                continue;
            }
            count += (int) candidate.getChildChunks().stream()
                .filter(child -> child != null && StrUtil.isNotBlank(child.getText()))
                .count();
        }
        return count;
    }

    private void updateStepExecuteStatus(Long planId, Integer executeStatus) {

        stepMapper.update(null, new LambdaUpdateWrapper<SuperAgentDocumentStrategyStep>()
            .eq(SuperAgentDocumentStrategyStep::getPlanId, planId)
            .eq(SuperAgentDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode())
            .set(SuperAgentDocumentStrategyStep::getExecuteStatus, executeStatus));
    }

    private List<SuperAgentDocumentStrategyStep> listSteps(Long planId) {
        List<SuperAgentDocumentStrategyStep> stepList = stepMapper.selectList(new LambdaQueryWrapper<SuperAgentDocumentStrategyStep>()
            .eq(SuperAgentDocumentStrategyStep::getPlanId, planId)
            .eq(SuperAgentDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode()));
        return stepList.stream()
            .sorted(Comparator
                .comparingInt((SuperAgentDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(SuperAgentDocumentStrategyStep::getStepNo)
                .thenComparing(SuperAgentDocumentStrategyStep::getId))
            .toList();
    }

    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    private int getNextPlanVersion(Long documentId) {

        List<SuperAgentDocumentStrategyPlan> planList = planMapper.selectList(new LambdaQueryWrapper<SuperAgentDocumentStrategyPlan>()
            .eq(SuperAgentDocumentStrategyPlan::getDocumentId, documentId)
            .eq(SuperAgentDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(SuperAgentDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return planList.isEmpty() ? 1 : planList.get(0).getPlanVersion() + 1;
    }

    private void finishTaskSuccess(SuperAgentDocumentTask task, Integer stage, Date startTime) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.SUCCESS.getCode());
        task.setCurrentStage(stage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode(null);
        task.setErrorMsg(null);
        taskMapper.updateById(task);
    }

    private void syncNavigationArtifacts(Long documentId,
                                         Long parseTaskId,
                                         List<SuperAgentDocumentStructureNode> structureNodes) {
        log.info("开始同步导航产物: documentId={}, parseTaskId={}, structureNodeCount={}",
            documentId,
            parseTaskId,
            structureNodes == null ? 0 : structureNodes.size());
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            log.info("同步导航 ES 索引: documentId={}, parseTaskId={}", documentId, parseTaskId);
            navigationIndexService.reindexDocumentNodes(documentId, parseTaskId, structureNodes);
        }
        else {
            log.info("跳过导航 ES 索引同步，因为服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            log.info("同步结构图投影: documentId={}, parseTaskId={}", documentId, parseTaskId);
            graphProjectionService.projectToGraph(documentId, parseTaskId);
        }
        else {
            log.info("跳过结构图投影，因为图服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
    }

    private void failTask(SuperAgentDocumentTask task, Date startTime, Exception exception, Integer currentStage) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.FAILED.getCode());
        task.setCurrentStage(currentStage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode("TASK_FAILED");
        task.setErrorMsg(exception.getMessage());
        taskMapper.updateById(task);
    }

    private int estimateTokenCount(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        int chineseCount = 0;
        int englishCount = 0;

        for (char current : text.toCharArray()) {
            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                chineseCount++;
            }
        }

        for (String word : text.split("\\s+")) {
            if (word.matches(".*[A-Za-z].*")) {
                englishCount++;
            }
        }

        return chineseCount + englishCount + Math.max(1, (text.length() - chineseCount) / 4);
    }

    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }

    private record ParentChildEntityBundle(
        List<SuperAgentDocumentParentBlock> parentBlocks,
        List<SuperAgentDocumentChunk> childChunks
    ) {
    }
}
