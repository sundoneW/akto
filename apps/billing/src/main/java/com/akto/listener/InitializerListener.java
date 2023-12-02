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
import com.akto.util.UsageCalculator;
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
                            UsageCalculator.instance.sendOrgUsageDataToAllSinks(o);
                            UsageCalculator.instance.aggregateUsageForOrg(o, finalUsageLowerBound, finalUsageUpperBound);
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
