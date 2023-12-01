package com.akto.utils.usage;

import java.util.Set;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.metadata.ActiveAccounts;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.utils.billing.OrganizationUtils;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class UsageMetricCalculator {

    public static int calculateActiveEndpoints(UsageMetric usageMetric) {
        /* todo:
         * Active endpoints def - endpoints which have been tested
         */
        int measureEpoch = usageMetric.getMeasureEpoch();

        int activeEndpoints = (int) ApiInfoDao.instance.count(
            Filters.gt(ApiInfo.LAST_SEEN, measureEpoch)
        );
        
        return activeEndpoints;
    }

    public static int calculateCustomTests(UsageMetric usageMetric) {
        int customTemplates = (int) YamlTemplateDao.instance.count(
            Filters.eq(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)
        );
        return customTemplates;
    }

    public static int calculateTestRuns(UsageMetric usageMetric) {
        int measureEpoch = usageMetric.getMeasureEpoch();

        int testRuns = (int) TestingRunResultDao.instance.count(
            Filters.gt(TestingRunResult.END_TIMESTAMP, measureEpoch)
        );
        return testRuns;
    }

    public static int calculateActiveAccounts(UsageMetric usageMetric) {
        String organizationId = usageMetric.getOrganizationId();

        if (organizationId == null) {
            return 0;
        }

        // Get organization
        Organization organization = OrganizationsDao.instance.findOne(
            Filters.eq(Organization.ID, organizationId)
        );

        if (organization == null) {
            return 0;
        }

        // String adminEmail = organization.getAdminEmail();

        // // Get admin user id
        // User admin = UsersDao.instance.findOne(
        //     Filters.eq(User.LOGIN, adminEmail)
        // );

        // if (admin == null) {
        //     return 0;
        // }

        // int adminUserId = admin.getId();

        // Get accounts belonging to organization
        //Set<Integer> accounts = OrganizationUtils.findAccountsBelongingToOrganization(adminUserId);
        Set<Integer> accounts = organization.getAccounts();

        Gson gson = new Gson();
        ActiveAccounts activeAccounts = new ActiveAccounts(accounts);
        String jsonString = gson.toJson(activeAccounts);
        usageMetric.setMetadata(jsonString);

        return accounts.size();
    }
    
    public static void calculateUsageMetric(UsageMetric usageMetric) {
        MetricTypes metricType = usageMetric.getMetricType();
        int usage = 0;

        usageMetric.setRecordedAt(Context.now());

        if (metricType != null) {
            switch (metricType) {
                case ACTIVE_ENDPOINTS:
                    usage = calculateActiveEndpoints(usageMetric);
                    break;
                case CUSTOM_TESTS:
                    usage = calculateCustomTests(usageMetric);
                    break;
                case TEST_RUNS:
                    usage = calculateTestRuns(usageMetric);
                    break;
                case ACTIVE_ACCOUNTS:
                    usage = calculateActiveAccounts(usageMetric);
                    break;
                default:
                    usage = 0;
                    break;
            }
        }

        usageMetric.setUsage(usage);
    }      
}