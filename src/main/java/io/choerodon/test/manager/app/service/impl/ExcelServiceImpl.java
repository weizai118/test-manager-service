package io.choerodon.test.manager.app.service.impl;

import com.google.common.collect.Lists;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.exception.CommonException;
import io.choerodon.test.manager.api.dto.*;
import io.choerodon.test.manager.app.service.*;
import io.choerodon.test.manager.domain.service.IExcelService;
import io.choerodon.test.manager.domain.service.ITestFileLoadHistoryService;
import io.choerodon.test.manager.domain.service.impl.ICycleCaseExcelServiceImpl;
import io.choerodon.test.manager.domain.service.impl.IReadMeExcelServiceImpl;
import io.choerodon.test.manager.domain.service.impl.ITestCaseExcelServiceImpl;
import io.choerodon.test.manager.domain.test.manager.entity.*;
import io.choerodon.test.manager.domain.test.manager.factory.TestCaseStepEFactory;
import io.choerodon.test.manager.domain.test.manager.factory.TestCycleEFactory;
import io.choerodon.test.manager.domain.test.manager.factory.TestIssueFolderEFactory;
import io.choerodon.test.manager.domain.test.manager.factory.TestIssueFolderRelEFactory;
import io.choerodon.test.manager.infra.common.utils.ExcelUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by 842767365@qq.com on 8/9/18.
 */

@Component
public class ExcelServiceImpl implements ExcelService {

    private static final String EXPORT_ERROR = "error.issue.export";
    private static final String EXPORT_ERROR_WORKBOOK_CLOSE = "error.issue.close.workbook";
    private static final String EXPORT_ERROR_SET_HEADER = "error.issue.set.workbook";


    private static final String EXPORTSUCCESSINFO = "导出测试详情：创建workbook成功，类型:";

    private static final String LOOKUPSHEETNAME = "数据源页";
    private static final String FILESUFFIX = ".xlsx";


    Log log = LogFactory.getLog(this.getClass());
    @Autowired
    TestCycleCaseService testCycleCaseService;

    @Autowired
    IExcelService iExcelService;

    @Autowired
    TestCycleService testCycleService;

    @Autowired
    TestCaseService testCaseService;

    @Autowired
    FileService fileFeignClient;

    @Autowired
    ITestFileLoadHistoryService iLoadHistoryService;


    /**
     * 设置http请求报文为下载文件
     *
     * @param request
     * @throws UnsupportedEncodingException
     **/
    private void setExcelHeaderByStream(HttpServletRequest request, HttpServletResponse response) {
        String charsetName = setExcelHeader(request);
        response.reset();
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        try {
            response.setHeader("Content-Disposition", "attachment;filename="
                    + new String((FILESUFFIX).getBytes(charsetName),
                    "ISO-8859-1"));
        } catch (UnsupportedEncodingException e1) {
            throw new CommonException(EXPORT_ERROR_SET_HEADER, e1);
        }

    }

    private String setExcelHeader(HttpServletRequest request) {
        String charsetName = "UTF-8";
        if (request.getHeader("User-Agent").contains("Firefox")) {
            charsetName = "GB2312";
        }
        return charsetName;
    }

    /**
     * 导出一个cycle下的测试详情，默认HSSFWorkBook
     *
     * @param cycleId
     * @param projectId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exportCycleCaseInOneCycle(Long cycleId, Long projectId, HttpServletRequest request,
                                          HttpServletResponse response,Long organizationId) {
        exportCycleCaseInOneCycleByTransaction(cycleId,projectId,request,response,organizationId);
    }

    /**
     * 导出项目下的所有用例，默认XSSF WorkBook
     *
     * @param projectId not null
     * @param request
     * @param response
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exportCaseByProject(Long projectId, HttpServletRequest request, HttpServletResponse response,Long organizationId) {
        exportCaseProjectByTransaction(projectId,request,response,organizationId);
    }

    /**
     * 导出项目下的所有用例，默认XSSF WorkBook
     *
     * @param versionId not null
     * @param request
     * @param response
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exportCaseByVersion(Long projectId, Long versionId, HttpServletRequest request, HttpServletResponse response,Long organizationId) {
        exportCaseVersionByTransaction(projectId,versionId,request,response,organizationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exportCaseByFolder(Long projectId, Long folderId, HttpServletRequest request, HttpServletResponse response,Long organizationId) {
        exportCaseFolderByTransaction(projectId,folderId,request,response,organizationId);
    }


    @Async
    public void exportCycleCaseInOneCycleByTransaction(Long cycleId, Long projectId, HttpServletRequest request,
                                                       HttpServletResponse response,Long organizationId) {
        setExcelHeader(request);
        Assert.notNull(cycleId, "error.export.cycle.in.one.cycleId.not.be.null");

        TestFileLoadHistoryE loadHistoryE = insertHistory(projectId, cycleId,
                TestFileLoadHistoryE.Source.CYCLE, TestFileLoadHistoryE.Action.DOWNLOAD_CYCLE);

        TestCycleE cycleE = TestCycleEFactory.create();
        cycleE.setCycleId(cycleId);
        Long[] cycleIds = Stream.concat(cycleE.getChildFolder().stream().map(TestCycleE::getCycleId), Stream.of(cycleId)).toArray(Long[]::new);
        TestCycleDTO cycle = ConvertHelper.convert(cycleE.queryOne(), TestCycleDTO.class);
        testCycleService.populateVersion(cycle, projectId);
        testCycleService.populateUsers(Lists.newArrayList(cycle));
        Map<Long, List<TestCycleCaseDTO>> cycleCaseMap = Optional.ofNullable(testCycleCaseService.queryCaseAllInfoInCyclesOrVersions(cycleIds, null, projectId,organizationId))
                .orElseGet(ArrayList::new).stream().collect(Collectors.groupingBy(TestCycleCaseDTO::getCycleId));
        IExcelService service = new <TestCycleDTO, TestCycleCaseDTO>ICycleCaseExcelServiceImpl();
        Workbook workbook = ExcelUtil.getWorkBook(ExcelUtil.Mode.XSSF);
        printDebug(EXPORTSUCCESSINFO + ExcelUtil.Mode.XSSF);
        String projectName = testCaseService.getProjectInfo(projectId).getName();
        service.exportWorkBookWithOneSheet(cycleCaseMap, projectName, cycle, workbook);
        String fileName = projectName + "-" + cycle.getCycleName() + FILESUFFIX;
        downloadWorkBook(workbook, fileName,loadHistoryE);
    }

    @Async
    public void exportCaseProjectByTransaction(Long projectId, HttpServletRequest request, HttpServletResponse response,Long organizationId) {
        setExcelHeader(request);

        TestFileLoadHistoryE loadHistoryE = insertHistory(projectId, projectId,
                TestFileLoadHistoryE.Source.PROJECT, TestFileLoadHistoryE.Action.DOWNLOAD_ISSUE);

        TestIssueFolderE folderE = TestIssueFolderEFactory.create();
        folderE.setProjectId(projectId);

        String projectName = testCaseService.getProjectInfo(projectId).getName();

        Long[] versionsId = testCaseService.getVersionIds(projectId);

        Workbook workbook = ExcelUtil.getWorkBook(ExcelUtil.Mode.XSSF);
        printDebug(EXPORTSUCCESSINFO + ExcelUtil.Mode.XSSF);
        IExcelService service = new <TestIssueFolderDTO, TestIssueFolderRelDTO>ITestCaseExcelServiceImpl();

        service.exportWorkBookWithOneSheet(new HashMap<>(), projectName, ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);
        for (Long versionId : versionsId) {
            folderE.setVersionId(versionId);
            service.exportWorkBookWithOneSheet(populateFolder(folderE,organizationId), projectName,
                    ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);
        }
        workbook.setSheetHidden(0, true);
        workbook.setActiveSheet(1);
        workbook.setSheetName(0, LOOKUPSHEETNAME);
        workbook.setSheetOrder(LOOKUPSHEETNAME, workbook.getNumberOfSheets() - 1);
        String fileName = projectName + FILESUFFIX;
        downloadWorkBook(workbook, fileName,loadHistoryE);
    }

    @Async
    public void exportCaseVersionByTransaction(Long projectId, Long versionId, HttpServletRequest request, HttpServletResponse response,Long organizationId) {
        setExcelHeader(request);
        Assert.notNull(versionId, "error.export.cycle.in.one.versionId.not.be.null");

        TestFileLoadHistoryE loadHistoryE = insertHistory(projectId, versionId,
                TestFileLoadHistoryE.Source.VERSION, TestFileLoadHistoryE.Action.DOWNLOAD_ISSUE);

        String projectName = testCaseService.getProjectInfo(projectId).getName();

        TestIssueFolderE folderE = TestIssueFolderEFactory.create();
        folderE.setProjectId(projectId);
        folderE.setVersionId(versionId);

        Workbook workbook = ExcelUtil.getWorkBook(ExcelUtil.Mode.XSSF);
        printDebug(EXPORTSUCCESSINFO + ExcelUtil.Mode.XSSF);
        IExcelService service = new <TestIssueFolderDTO, TestIssueFolderRelDTO>ITestCaseExcelServiceImpl();
        service.exportWorkBookWithOneSheet(new HashMap<>(), projectName, ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);
        service.exportWorkBookWithOneSheet(populateFolder(folderE,organizationId), projectName, ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);

        workbook.setSheetHidden(0, true);
        workbook.setActiveSheet(1);
        workbook.setSheetName(0, LOOKUPSHEETNAME);
        workbook.setSheetOrder(LOOKUPSHEETNAME, workbook.getNumberOfSheets() - 1);
        String fileName = projectName + "-" + workbook.getSheetName(0).substring(2) + FILESUFFIX;
        downloadWorkBook(workbook, fileName,loadHistoryE);
    }


    @Async
    public void exportCaseFolderByTransaction(Long projectId, Long folderId, HttpServletRequest request, HttpServletResponse response,Long organizationId) {
        setExcelHeader(request);
        Assert.notNull(projectId, "error.export.cycle.in.one.folderId.not.be.null");

        TestFileLoadHistoryE loadHistoryE = insertHistory(projectId, folderId,
                TestFileLoadHistoryE.Source.FOLDER,TestFileLoadHistoryE.Action.DOWNLOAD_ISSUE);

        String projectName = testCaseService.getProjectInfo(projectId).getName();

        TestIssueFolderE folderE = TestIssueFolderEFactory.create();
        folderE.setProjectId(projectId);
        folderE.setFolderId(folderId);

        Workbook workbook = ExcelUtil.getWorkBook(ExcelUtil.Mode.XSSF);
        printDebug(EXPORTSUCCESSINFO + ExcelUtil.Mode.XSSF);
        IExcelService service = new <TestIssueFolderDTO, TestIssueFolderRelDTO>ITestCaseExcelServiceImpl();

        service.exportWorkBookWithOneSheet(new HashMap<>(), projectName, ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);
        service.exportWorkBookWithOneSheet(populateFolder(folderE,organizationId), projectName, ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);

        workbook.setSheetHidden(0, true);
        workbook.setActiveSheet(1);
        workbook.setSheetName(0, LOOKUPSHEETNAME);
        workbook.setSheetOrder(LOOKUPSHEETNAME, workbook.getNumberOfSheets() - 1);
        String fileName = projectName + "-" + workbook.getSheetName(0).substring(2) + "-" + folderE.queryByPrimaryKey(folderId).getName() + FILESUFFIX;
        downloadWorkBook(workbook, fileName,loadHistoryE);
    }

    @Override
    public void exportCaseTemplate(Long projectId, HttpServletRequest request, HttpServletResponse response) {
        setExcelHeaderByStream(request, response);

        String projectName = testCaseService.getProjectInfo(projectId).getName();

        TestIssueFolderE folderE = TestIssueFolderEFactory.create();
        folderE.setProjectId(projectId);

        Workbook workbook = ExcelUtil.getWorkBook(ExcelUtil.Mode.XSSF);
        printDebug(EXPORTSUCCESSINFO + ExcelUtil.Mode.XSSF);

        Long[] versionsId = testCaseService.getVersionIds(projectId);

        Map<Long, List<TestIssueFolderRelDTO>> map = new HashMap<>();
        List<TestIssueFolderRelDTO> testIssueFolderRelDTOS = new ArrayList<>();
        TestIssueFolderRelDTO testIssueFolderRelDTO = new TestIssueFolderRelDTO();
        IssueInfosDTO issueInfosDTO = new IssueInfosDTO();
        issueInfosDTO.setAssigneeId(1L);
        issueInfosDTO.setPriorityCode("1");
        testIssueFolderRelDTO.setFolderId(1L);
        testIssueFolderRelDTO.setIssueInfosDTO(issueInfosDTO);
        testIssueFolderRelDTOS.add(testIssueFolderRelDTO);
        map.put(1L, testIssueFolderRelDTOS);

        IExcelService service = new <TestIssueFolderDTO, TestIssueFolderRelDTO>ITestCaseExcelServiceImpl();
        //准备lookup页
        service.exportWorkBookWithOneSheet(new HashMap<>(), projectName,
                ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);
        for (Long versionId : versionsId) {
            Object needMap = ((HashMap<Long, List<TestIssueFolderRelDTO>>) map).clone();
            folderE.setVersionId(versionId);
            service.exportWorkBookWithOneSheet((Map<Long, List>) needMap, projectName,
                    ConvertHelper.convert(folderE, TestIssueFolderDTO.class), workbook);
        }
        //准备README页
        IExcelService readMeService = new <ExcelReadMeDTO, ExcelReadMeOptionDTO>IReadMeExcelServiceImpl();
        Map<String, List<ExcelReadMeOptionDTO>> readMeMap = new HashMap<>();
        ExcelReadMeDTO readMeDTO = new ExcelReadMeDTO();
        readMeMap.put(readMeDTO.getHeader(), populateReadMeOptions());
        readMeService.exportWorkBookWithOneSheet(readMeMap, projectName, readMeDTO, workbook);

        workbook.setSheetName(versionsId.length + 1, "README");
        workbook.setSheetName(0, LOOKUPSHEETNAME);
        workbook.setSheetOrder(LOOKUPSHEETNAME, workbook.getNumberOfSheets() - 1);
        workbook.setSheetHidden(workbook.getNumberOfSheets() - 1, true);
        workbook.setSheetOrder("README", 0);
        workbook.setActiveSheet(0);
        downloadWorkBookByStream(workbook, response);
    }


    private String downloadWorkBook(Workbook workbook, String fileName,TestFileLoadHistoryE loadHistoryE) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();) {
            workbook.write(os);
            byte[] content = os.toByteArray();
            MultipartFile file = new MultipartExcel("file", fileName, "application/vnd.ms-excel", content);

            ResponseEntity<String> res = fileFeignClient.uploadFile(TestCycleCaseAttachmentRelE.ATTACHMENT_BUCKET, fileName, file);

            if(res.getStatusCode().is2xxSuccessful()) {
                loadHistoryE.setStatus(TestFileLoadHistoryE.Status.SUCCESS);
                loadHistoryE.setFileUrl(res.getBody());
            }else {
                loadHistoryE.setStatus(TestFileLoadHistoryE.Status.FAILURE);
                loadHistoryE.setFileStream(os.toString());
            }
            return res.getBody();
        } catch (IOException e) {
            loadHistoryE.setStatus(TestFileLoadHistoryE.Status.FAILURE);
            throw new CommonException(EXPORT_ERROR, e);
        } finally {
            try {
                iLoadHistoryService.update(loadHistoryE);
                workbook.close();
            } catch (IOException e) {
                log.warn(EXPORT_ERROR_WORKBOOK_CLOSE, e);
            }
        }
    }

    private void downloadWorkBookByStream(Workbook workbook, HttpServletResponse response) {
        try {
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new CommonException(EXPORT_ERROR, e);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                log.warn(EXPORT_ERROR_WORKBOOK_CLOSE, e);
            }
        }
    }

    private Map<Long, List<TestIssueFolderRelDTO>> populateFolder(TestIssueFolderE folderE,Long organizationId) {
        List<TestIssueFolderE> folders = Optional.ofNullable(folderE.queryAllUnderProject()).orElseGet(ArrayList::new);

        Map<Long, List<TestIssueFolderRelDTO>> folderRelMap = new HashMap<>();

        TestCaseStepE caseStepE = TestCaseStepEFactory.create();

        Map<Long, List<TestCaseStepDTO>> caseStepMap = Optional.ofNullable(ConvertHelper.convertList(caseStepE.querySelf(), TestCaseStepDTO.class))
                .orElseGet(ArrayList::new).stream().collect(Collectors.groupingBy(TestCaseStepDTO::getIssueId));

        for (TestIssueFolderE folder : folders) {
            TestIssueFolderRelE folderRelE = TestIssueFolderRelEFactory.create();
            folderRelE.setFolderId(folder.getFolderId());
            List<TestIssueFolderRelE> folderRels = folderRelE.queryAllUnderProject();

            List<TestIssueFolderRelDTO> folderRelDTOS = new ArrayList<>();

            List<Long> issueIds = folderRels.stream().map(TestIssueFolderRelE::getIssueId).collect(Collectors.toList());

            Map<Long, IssueInfosDTO> issueInfosMap = batchGetIssueInfo(issueIds, folderE,organizationId);

            for (TestIssueFolderRelE folderRel : folderRels) {
                TestIssueFolderRelDTO needRel = ConvertHelper.convert(folderRel, TestIssueFolderRelDTO.class);
                needRel.setIssueInfosDTO(issueInfosMap.get(folderRel.getIssueId()));
                needRel.setFolderName(folder.getName());

                needRel.setTestCaseStepDTOS(caseStepMap.get(folderRel.getIssueId()));

                folderRelDTOS.add(needRel);
            }

            if (folderE.getVersionId() == null && folderE.getFolderId() != null) {
                folderE.setVersionId(folder.getVersionId());
            }
            folderRelMap.put(folder.getFolderId(), folderRelDTOS);
        }
        return folderRelMap;
    }

    private Map<Long, IssueInfosDTO> batchGetIssueInfo(List<Long> issueIds, TestIssueFolderE folderE,Long organizationId) {
        Map<Long, IssueInfosDTO> issueInfosMap = new HashMap<>();

        int flag = issueIds.size() / 400;
        for (int j = 0; j <= flag; j++) {
            Long[] toSendIds;
            if (issueIds.size() > 400 && j != flag) {
                toSendIds = issueIds.subList(j * 400, (j + 1) * 400).toArray(new Long[400]);
            } else {
                toSendIds = issueIds.subList(j * 400, issueIds.size()).toArray(new Long[400]);
            }

            if (!ObjectUtils.isEmpty(issueIds)) {
                printDebug("开始分批获取issue信息（最大400一批），当前第" + (j + 1) + "批");
                issueInfosMap.putAll(testCaseService.getIssueInfoMap(folderE.getProjectId(), toSendIds, true,organizationId));
            }
        }
        return issueInfosMap;
    }

    private List<ExcelReadMeOptionDTO> populateReadMeOptions() {
        List<ExcelReadMeOptionDTO> optionDTOS = new ArrayList<>();
        optionDTOS.add(new ExcelReadMeOptionDTO("文件夹", true));
        optionDTOS.add(new ExcelReadMeOptionDTO("用例概要", true));
        optionDTOS.add(new ExcelReadMeOptionDTO("用例编号", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("优先级", true));
        optionDTOS.add(new ExcelReadMeOptionDTO("用例描述", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("经办人", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("状态", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("测试步骤", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("测试数据", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("预期结果", false));
        optionDTOS.add(new ExcelReadMeOptionDTO("文件夹ID(系统自动生成)", null));
        optionDTOS.add(new ExcelReadMeOptionDTO("优先级valueCode(系统自动生成)", null));
        optionDTOS.add(new ExcelReadMeOptionDTO("经办人ID(系统自动生成)", null));

        return optionDTOS;
    }

    private void printDebug(String info) {
        if (log.isDebugEnabled()) {
            log.debug(info);
        }
    }

    private TestFileLoadHistoryE insertHistory(Long projectId, Long optionalParam, TestFileLoadHistoryE.Source source,TestFileLoadHistoryE.Action action) {
        TestFileLoadHistoryE loadHistoryE = new TestFileLoadHistoryE(projectId, action, source, optionalParam, TestFileLoadHistoryE.Status.SUSPENDING);
        return iLoadHistoryService.insertOne(loadHistoryE);
    }

}
