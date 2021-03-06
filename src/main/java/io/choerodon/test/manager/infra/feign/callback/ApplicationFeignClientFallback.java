package io.choerodon.test.manager.infra.feign.callback;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.dto.ApplicationRepDTO;
import io.choerodon.devops.api.dto.ApplicationVersionRepDTO;
import io.choerodon.test.manager.infra.feign.ApplicationFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Created by zongw.lee@gmail.com on 26/11/2018
 */
@Component
public class ApplicationFeignClientFallback implements ApplicationFeignClient {

    private static final String QUERY_VERSION_VALUE_ERROR = "error.ApplicationFeign.query.version.value";
    private static final String QUERY_APP_ERROR = "error.ApplicationFeign.query.app";
    private static final String QUERY_VERSION_ERROR = "error.ApplicationFeign.query.version";

    @Override
    public ResponseEntity<String> getVersionValue(Long projectId, Long appVersionId) {
        throw new CommonException(QUERY_VERSION_VALUE_ERROR);
    }

    @Override
    public ResponseEntity<ApplicationRepDTO> queryByAppId(Long projectId, Long applicationId) {
        throw new CommonException(QUERY_APP_ERROR);
    }

    @Override
    public ResponseEntity<ApplicationVersionRepDTO> getAppversion(Long projectId, Long appVersionId) {
        throw new CommonException(QUERY_VERSION_ERROR);
    }
}
