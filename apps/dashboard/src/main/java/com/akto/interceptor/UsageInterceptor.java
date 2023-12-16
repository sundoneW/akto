package com.akto.interceptor;

import java.util.HashMap;
import java.util.Map;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.filter.UserDetailsFilter;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

public class UsageInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageInterceptor.class);

    String featureLabel;

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    final static String UNAUTHORIZED = "UNAUTHORIZED";

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {

        try {
            Map<String, Object> session = invocation.getInvocationContext().getSession();
            int sessionAccId = (Integer) session.get(UserDetailsFilter.ACCOUNT_ID);

            Organization organization = OrganizationsDao.instance.findOne(
                    Filters.in(Organization.ACCOUNTS, sessionAccId));

            if (organization == null) {
                throw new Exception("Organization not found");
            }

            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();

            FeatureAccess featureAccess = featureWiseAllowed.get(featureLabel);

            if (UsageInterceptorUtil.checkContextSpecificFeatureAccess(invocation, featureLabel)) {

                /*
                 * if the feature doesn't exist in the entitlements map,
                 * then the user is unauthorized to access the feature
                 */
                if (featureAccess == null ||
                        !featureAccess.getIsGranted()) {
                    ((ActionSupport) invocation.getAction())
                            .addActionError("This feature is not available in your plan.");
                    return UNAUTHORIZED;
                }
                if (featureAccess.checkOverageAfterGrace()) {
                    ((ActionSupport) invocation.getAction())
                            .addActionError("You have exceeded the limit of this feature.");
                    return UNAUTHORIZED;
                }

            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in UsageInterceptor " + e.toString(), LogDb.DASHBOARD);
        }

        return invocation.invoke();
    }

}