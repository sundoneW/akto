package com.akto.listener;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.servlet.ServletContextListener;

import com.akto.DaoInit;
import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dao.billing.OrganizationUsageDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dao.usage.UsageSyncDao;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.OrganizationUsage;
import com.akto.dto.billing.OrganizationUsage.DataSink;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageSync;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.stigg.StiggReporterClient;
import com.akto.util.UsageUtils;
import com.akto.util.tasks.OrganizationTask;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.mockito.internal.matchers.Or;

import static com.akto.dto.billing.OrganizationUsage.ORG_ID;
import static com.akto.dto.billing.OrganizationUsage.SINKS;

public class InitializerListener implements ServletContextListener {
    public static boolean connectedToMongo = false;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class);

    private static int epochToDateInt(int epoch) {
        Date dateFromEpoch = new Date( epoch * 1000L );
        Calendar cal = Calendar.getInstance();
        cal.setTime(dateFromEpoch);
        return cal.get(Calendar.YEAR) * 10000 + cal.get(Calendar.MONTH) * 100 + cal.get(Calendar.DAY_OF_MONTH);

    }

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        String mongoURI = System.getenv("BILLING_DB_CONN_URL");
        System.out.println("MONGO URI " + mongoURI);

        executorService.schedule(new Runnable() {
            public void run() {
                boolean calledOnce = false;
                do {
                    try {
                        if (!calledOnce) {
                            DaoInit.init(new ConnectionString(mongoURI));
                            calledOnce = true;
                        }
                        checkMongoConnection();
                        runInitializerFunctions();
                        setupUsageReportingScheduler();
                        //test();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(String.format("Error: %s", e.getMessage()), LogDb.BILLING);
                    } finally {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } while (!connectedToMongo);
            }
        }, 0, TimeUnit.SECONDS);
    }

    private static void checkMongoConnection() throws Exception {
        AccountsDao.instance.getStats();
        connectedToMongo = true;
    }

    public static void runInitializerFunctions() {
        OrganizationUsageDao.createIndexIfAbsent();
        System.out.println("Running initializer functions");
    }

    @Override
    public void contextDestroyed(javax.servlet.ServletContextEvent sce) {
        // override
    }

    private void sendOrgUsageDataToAllSinks(Organization o) {
        Bson filterQ =
                Filters.and(
                    Filters.eq(ORG_ID, o.getId()),
                    Filters.eq(SINKS, new BasicDBObject())
                );
        List<OrganizationUsage> pendingUsages =
            OrganizationUsageDao.instance.findAll(filterQ);


        for (OrganizationUsage lastUsageItem: pendingUsages) {

            int today = epochToDateInt(Context.now());
            //if (lastUsageItem.getDate() == today) return;

            for (DataSink dataSink : DataSink.values()) {
                switch (dataSink) {
                    case STIGG:
                        syncBillingEodWithStigg(lastUsageItem);
                        break;
                    case MIXPANEL:
                        //syncBillingEodWithMixpanel(lastUsageItem);
                        break;
                    case SLACK:
                        //syncBillingEodWithSlack(lastUsageItem);
                        break;
                    default:
                        throw new IllegalStateException("Not a valid data sink. Found: " + dataSink);
                }
            }
        }

    }

    private void syncBillingEodWithSlack(OrganizationUsage lastUsageItem) {

    }

    private void syncBillingEodWithMixpanel(OrganizationUsage lastUsageItem) {
        
    }

    private void syncBillingEodWithStigg(OrganizationUsage lastUsageItem) {
        loggerMaker.infoAndAddToDb(String.format("Syncing usage for organization %s to Stigg", lastUsageItem.getOrgId()), LogDb.BILLING);

        for(Map.Entry<String, Integer> entry: lastUsageItem.getOrgMetricMap().entrySet()) {
            MetricTypes metricType = MetricTypes.valueOf(entry.getKey());
            String featureId =  metricType.getLabel();
            int value = entry.getValue();

            try {
                StiggReporterClient.instance.reportUsage(value, lastUsageItem.getOrgId(), featureId);

                Map<String, Integer> sinks = lastUsageItem.getSinks();
                sinks.put(DataSink.STIGG.toString(), Context.now());

                OrganizationUsageDao.instance.updateOne(
                        Filters.and(
                                Filters.eq(OrganizationUsage.ORG_ID, lastUsageItem.getOrgId()),
                                Filters.eq(OrganizationUsage.DATE, lastUsageItem.getDate())
                        ),
                        Updates.set(SINKS, sinks)
                );
            } catch (IOException e) {
                String errLog = "error while saving to Stigg: " + lastUsageItem.getOrgId() + " " + lastUsageItem.getDate() + " " + featureId;
                loggerMaker.errorAndAddToDb(errLog, LogDb.BILLING);
            }
        }
    }

    private void aggregateUsageForOrg(Organization o, int usageLowerBound, int usageUpperBound) {
        try {
            String organizationId = o.getId();
            String organizationName = o.getName();
            Set<Integer> accounts = o.getAccounts();

            loggerMaker.infoAndAddToDb(String.format("Reporting usage for organization %s - %s", organizationId, organizationName), LogDb.BILLING);

            loggerMaker.infoAndAddToDb(String.format("Calculating Consolidated and account wise usage for organization %s - %s", organizationId, organizationName), LogDb.BILLING);

            Map<String, Integer> consolidatedUsage = new HashMap<String, Integer>();

            // Calculate account wise usage and consolidated usage
            for (MetricTypes metricType : MetricTypes.values()) {
                String metricTypeString = metricType.toString();
                consolidatedUsage.put(metricTypeString, 0);

                for (int account : accounts) {
                    UsageMetric usageMetric = UsageMetricsDao.instance.findLatestOne(
                            Filters.and(
                                    Filters.eq(UsageMetric.ORGANIZATION_ID, organizationId),
                                    Filters.eq(UsageMetric.ACCOUNT_ID, account),
                                    Filters.eq(UsageMetric.METRIC_TYPE, metricType),
                                    Filters.and(
                                            Filters.gte(UsageMetric.AKTO_SAVE_EPOCH, usageLowerBound),
                                            Filters.lt(UsageMetric.AKTO_SAVE_EPOCH, usageUpperBound)
                                    )
                            )
                    );

                    int usage = 0;

                    if (usageMetric != null) {
                        usage = usageMetric.getUsage();
                    }

                    int currentConsolidateUsage = consolidatedUsage.get(metricTypeString);
                    int updatedConsolidateUsage = currentConsolidateUsage + usage;

                    consolidatedUsage.put(metricTypeString, updatedConsolidateUsage);
                }
            }

            int date = epochToDateInt(usageLowerBound);

            OrganizationUsageDao.instance.insertOne(
                new OrganizationUsage(organizationId, date, Context.now(), consolidatedUsage, new HashMap<>())
            );

            loggerMaker.infoAndAddToDb(String.format("Consolidated and account wise usage for organization %s - %s calculated", organizationId, organizationName), LogDb.BILLING);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error while reporting usage for organization %s - %s. Error: %s", o.getId(), o.getName(), e.getMessage()), LogDb.BILLING);
        }
    }

    public void setupUsageReportingScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                loggerMaker.infoAndAddToDb(String.format("Running usage reporting scheduler"), LogDb.BILLING);

                UsageSync usageSync = UsageSyncDao.instance.findOne(
                        Filters.eq(UsageSync.SERVICE, "billing")
                );

                if (usageSync == null) {
                    int now = Context.now();
                    int startOfDayEpoch = now - (now % 86400) - 86400;
                    usageSync = new UsageSync("billing", startOfDayEpoch);
                    UsageSyncDao.instance.insertOne(usageSync);
                }

                int usageLowerBound = usageSync.getLastSyncStartEpoch(); // START OF THE DAY
                int usageMaxUpperBound = Context.now(); // NOW
                int usageUpperBound = usageLowerBound + UsageUtils.USAGE_UPPER_BOUND_DL; // END OF THE DAY

                while (usageUpperBound < usageMaxUpperBound) {
                    int finalUsageLowerBound = usageLowerBound;
                    int finalUsageUpperBound = usageUpperBound;

                    loggerMaker.infoAndAddToDb(String.format("Lower Bound: %d Upper bound: %d", usageLowerBound, usageUpperBound), LogDb.BILLING);
                    OrganizationTask.instance.executeTask(new Consumer<Organization>() {
                        @Override
                        public void accept(Organization o) {
                            sendOrgUsageDataToAllSinks(o);
                            aggregateUsageForOrg(o, finalUsageLowerBound, finalUsageUpperBound);
                        }
                    }, "usage-reporting-scheduler");

                    UsageSyncDao.instance.updateOne(
                            Filters.eq(UsageSync.SERVICE, "billing"),
                            Updates.set(UsageSync.LAST_SYNC_START_EPOCH, usageUpperBound)
                    );
                    usageLowerBound = usageUpperBound;
                    usageUpperBound += 86400;
                }
            }
        }, 0, 1, UsageUtils.USAGE_CRON_PERIOD);
    }

}