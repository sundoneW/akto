package com.akto.analyser;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.*;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.akto.util.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.*;

public class ResourceAnalyser {
    BloomFilter<CharSequence> duplicateCheckerBF;
    BloomFilter<CharSequence> valuesBF;
    Map<String, ParamTypeInfo> countMap = new HashMap<>();

    int last_sync = 0;

    public ResourceAnalyser(int duplicateCheckerBfSize, double duplicateCheckerBfFpp, int valuesBfSize, double valuesBfFpp) {
        duplicateCheckerBF = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), duplicateCheckerBfSize, duplicateCheckerBfFpp
        );

        valuesBF = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), valuesBfSize, valuesBfFpp
        );

        syncWithDb();
    }

    public static final String X_FORWARDED_FOR = "x-forwarded-for";

    public URLTemplate matchWithUrlTemplate(int apiCollectionId, String url, String method) {
        Catalog catalog = catalogMap.get(apiCollectionId);
        if (catalog == null) return null;
        URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));
        for (URLTemplate urlTemplate: catalog.templateUrls) {
            if (urlTemplate.match(urlStatic)) return urlTemplate;
        }
        return null;
    }


    public void analyse(HttpResponseParams responseParams) {
        if (responseParams.statusCode < 200 || responseParams.statusCode >= 300) return;

        if (countMap.keySet().size() > 200_000 || (Context.now() - last_sync) > 120) {
            syncWithDb();
        }


        HttpRequestParams requestParams = responseParams.getRequestParams();
        String urlWithParams = requestParams.getURL();

        // user id
        List<String> ipList = responseParams.getRequestParams().getHeaders().get(X_FORWARDED_FOR);
        if (ipList == null || ipList.isEmpty()) return;
        String userId = ipList.get(0);

        // get actual api collection id
        Integer apiCollectionId = requestParams.getApiCollectionId();
        String hostName = HttpCallParser.getHostName(requestParams.getHeaders());
        apiCollectionId = findTrueApiCollectionId(apiCollectionId, hostName, responseParams.getSource());

        if (apiCollectionId == null) return;

        String method = requestParams.getMethod();
        String originalUrl = requestParams.getURL()+"";

        // get actual url
        URLStatic urlStatic = URLAggregator.getBaseURL(requestParams.getURL(), method);
        String url = urlStatic.getUrl();

        URLTemplate urlTemplate = matchWithUrlTemplate(apiCollectionId, url, method);
        if (urlTemplate != null) {
            url = urlTemplate.getTemplateString();
        }

        String combinedUrl = apiCollectionId + "#" + url + "#" + method;

        // analyse url params
        if (urlTemplate != null) {
            String[] tokens = APICatalogSync.tokenize(originalUrl);
            SingleTypeInfo.SuperType[] types = urlTemplate.getTypes();
            int size = tokens.length;
            for (int idx=0; idx < size; idx++) {
                SingleTypeInfo.SuperType type = types[idx];
                String value = tokens[idx];
                if (type != null) {
                    analysePayload(value, idx+"", combinedUrl, userId, url, method, -1,
                            apiCollectionId, false, true);
                }
            }
        }

        // analyse request payload
        BasicDBObject payload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams);
        Map<String, Set<Object>> flattened = JSONUtils.flatten(payload);
        for (String param: flattened.keySet()) {
            for (Object val: flattened.get(param) ) {
                analysePayload(val, param, combinedUrl, userId, url,
                        method, -1, apiCollectionId, false, false);
            }
        }

        // analyse request headers
//        Map<String, List<String>> requestHeaders = requestParams.getHeaders();
//        for (String headerName: requestHeaders.keySet()) {
//            if (StandardHeaders.isStandardHeader(headerName)) continue;
//            List<String> headerValues = requestHeaders.get(headerName);
//            if (headerValues == null) {
//                headerValues = Collections.singletonList("null");
//            }
//            for (String headerValue: headerValues) {
//                analysePayload(headerValue, headerName, combinedUrl, userId, url,
//                        method, -1, apiCollectionId, true, false);
//            }
//        }
    }


    public void analysePayload(Object paramObject, String param, String combinedUrl, String userId,
                               String url, String method, int statusCode, int apiCollectionId, boolean isHeader,
                               boolean isUrlParam) {
        String paramValue = convertToParamValue(paramObject);
        if (paramValue == null) return ;

        ParamTypeInfo paramTypeInfo = new ParamTypeInfo(apiCollectionId, url, method, statusCode,isHeader, isUrlParam, param);

        // check if moved
        boolean moved = checkIfMoved(combinedUrl, param, paramValue);
        if (moved) return;

        // check if duplicate
        boolean isNew = checkDuplicate(userId, combinedUrl,param, paramValue);
        if (!isNew) return;

        // check if present
        boolean present = checkIfPresent(combinedUrl, param, paramValue);
        ParamTypeInfo paramTypeInfo1 = countMap.computeIfAbsent(paramTypeInfo.composeKey(), k -> paramTypeInfo);
        if (present) {
            markMoved(combinedUrl, param, paramValue);
            paramTypeInfo1.incPublicCount(1);
        } else {
            addToValueBF(combinedUrl, param, paramValue);
            paramTypeInfo1.incUniqueCount(1);
        }
    }


    public static class Catalog {
        List<URLTemplate> templateUrls;
        Set<URLStatic> strictUrls;

        public Catalog() {
            this.templateUrls = new ArrayList<>();
            this.strictUrls = new HashSet<>();
        }

    }

    public Map<Integer, Catalog> catalogMap = new HashMap<>();

    public void buildCatalog() {
        List<ApiInfo.ApiInfoKey> apis = SingleTypeInfoDao.instance.fetchEndpointsInCollection(null);
        for (ApiInfo.ApiInfoKey apiInfoKey: apis) {

            int apiCollectionId = apiInfoKey.getApiCollectionId();
            String url = apiInfoKey.getUrl();
            String method = apiInfoKey.getMethod().name();

            Catalog catalog = catalogMap.get(apiCollectionId);
            if (catalog == null) {
                catalog = new Catalog();
                catalogMap.put(apiCollectionId, catalog);
            }

            List<URLTemplate> urlTemplates = catalog.templateUrls;
            Set<URLStatic> strictUrls = catalog.strictUrls;

            if (APICatalog.isTemplateUrl(url)) {
                URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url, URLMethods.Method.valueOf(method));
                urlTemplates.add(urlTemplate);
            } else {
                URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));
                strictUrls.add(urlStatic);
            }
        }

    }


    public List<WriteModel<ParamTypeInfo>> clean() {
        List<WriteModel<ParamTypeInfo>> bulkUpdates = new ArrayList<>();
        List<ApiInfo.ApiInfoKey> apis = ParamTypeInfoDao.instance.fetchEndpointsInCollection();
        for (ApiInfo.ApiInfoKey apiInfoKey: apis) {
            int apiCollectionId = apiInfoKey.getApiCollectionId();
            String url = apiInfoKey.url;
            URLMethods.Method method = apiInfoKey.getMethod();
            Catalog catalog = catalogMap.get(apiCollectionId);
            if (catalog == null) {
                bulkUpdates.add(new DeleteManyModel<>(Filters.eq(ParamTypeInfo.API_COLLECTION_ID, apiCollectionId)));
                continue;
            }

            URLStatic urlStatic = new URLStatic(url, method);
            if (catalog.strictUrls.contains(urlStatic)) {
                continue;
            }

            String trimmedUrl = APICatalogSync.trim(url);
            if (catalog.strictUrls.contains(new URLStatic(trimmedUrl, method))) {
                continue;
            }

            boolean flag = false;
            for (URLTemplate urlTemplate: catalog.templateUrls) {
                if (urlTemplate.match(urlStatic)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                Bson filter = Filters.and(
                        Filters.eq(ParamTypeInfo.API_COLLECTION_ID, apiCollectionId),
                        Filters.eq(ParamTypeInfo.URL, url),
                        Filters.eq(ParamTypeInfo.METHOD, method.name())
                );
                bulkUpdates.add(new DeleteManyModel<>(filter));
            }

        }

        return bulkUpdates;
    }



    public void syncWithDb() {
        buildCatalog();
        populateHostNameToIdMap();

        List<WriteModel<ParamTypeInfo>> dbUpdates = clean();
        System.out.println("delete count: " + dbUpdates.size());
        dbUpdates.addAll(getDbUpdatesForParamTypeInfo());
        System.out.println("total count: " + dbUpdates.size());
        countMap = new HashMap<>();
        last_sync = Context.now();
        if (dbUpdates.size() > 0) {
            ParamTypeInfoDao.instance.getMCollection().bulkWrite(dbUpdates);
        }
    }

    public List<WriteModel<ParamTypeInfo>> getDbUpdatesForParamTypeInfo() {
        List<WriteModel<ParamTypeInfo>> bulkUpdates = new ArrayList<>();
        for (ParamTypeInfo paramTypeInfo: countMap.values()) {
            if (paramTypeInfo.uniqueCount == 0 && paramTypeInfo.getPublicCount() == 0) continue;
            Bson filter = ParamTypeInfoDao.createFilters(paramTypeInfo);
            Bson update = Updates.combine(
                    Updates.inc(ParamTypeInfo.UNIQUE_COUNT, paramTypeInfo.getUniqueCount()),
                    Updates.inc(ParamTypeInfo.PUBLIC_COUNT, paramTypeInfo.getPublicCount())
            );
            bulkUpdates.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }

        return bulkUpdates;
    }

    public boolean checkDuplicate(String userId, String combinedUrl, String paramName, String paramValue) {
        String a = userId + "$" + combinedUrl + "$" + paramName + "$" + paramValue;
        return duplicateCheckerBF.put(a);
    }

    public boolean checkIfMoved(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue + "$moved";
        return valuesBF.mightContain(a);
    }

    public void markMoved(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue + "$moved";
        valuesBF.put(a);
    }

    public boolean checkIfPresent(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue;
        return valuesBF.mightContain(a);
    }

    public void addToValueBF(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue;
        valuesBF.put(a);
    }

    public String convertToParamValue(Object value) {
        if (value == null) return "null";
        return value.toString();
    }

    private Map<String, Integer> hostNameToIdMap = new HashMap<>();

    public Integer findTrueApiCollectionId(int originalApiCollectionId, String hostName, HttpResponseParams.Source source) {
        if (!HttpCallParser.useHostCondition(hostName, source)) {
            return originalApiCollectionId;
        }

        String key = hostName + "$" + originalApiCollectionId;
        Integer trueApiCollectionId = null;

        if (hostNameToIdMap.containsKey(key)) {
            trueApiCollectionId = hostNameToIdMap.get(key);
        }

        // todo: what if we don't find because of cycles

        return trueApiCollectionId;
    }

    public void populateHostNameToIdMap() {
        hostNameToIdMap = new HashMap<>();
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        for (ApiCollection apiCollection: apiCollectionList) {
            String key = apiCollection.getHostName() + "$" + apiCollection.getVxlanId();
            hostNameToIdMap.put(key, apiCollection.getId());
        }
    }


    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        List<ParamTypeInfo> paramTypeInfoList = ParamTypeInfoDao.instance.findAll(
                Filters.and(
                        Filters.eq(ParamTypeInfo.IS_HEADER, true),
                        Filters.gte(ParamTypeInfo.UNIQUE_COUNT, 5)
                )
        );
        Set<String> v = new HashSet<>();
        for (ParamTypeInfo paramTypeInfo: paramTypeInfoList) {
            v.add(paramTypeInfo.getParam());
        }

        for (String vv: v) {
            System.out.println("headers.add(\"" + vv + "\")");
        }
    }

    public static void main1(String[] args) throws Exception {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        ApiCollection.useHost = true;
        ResourceAnalyser resourceAnalyser = new ResourceAnalyser(10_000_000, 0.01, 10_000_000, 0.01);

        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());
        int i = 0;
        for (SampleData sampleData:sampleDataList) {
            System.out.println(i);
            for (String s: sampleData.getSamples()) {
                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(s);
                resourceAnalyser.analyse(httpResponseParams);
            }
            i ++;
        }

        MongoCursor<SensitiveSampleData> cursor = SensitiveSampleDataDao.instance.getMCollection().find().cursor();
        i = 0;
        while (cursor.hasNext()) {
            SensitiveSampleData sensitiveSampleData = cursor.next();
            for (String s: sensitiveSampleData.getSampleData()) {
                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(s);
                resourceAnalyser.analyse(httpResponseParams);
            }
           System.out.println(i);
            i+=1;
        }

        System.out.println("DONE");

        for (ParamTypeInfo paramTypeInfo: resourceAnalyser.countMap.values()) {
            long a = paramTypeInfo.uniqueCount;
            long b = paramTypeInfo.publicCount;

            if (a > 10 && (1.0*b)/a < 0.1) {
                System.out.println(paramTypeInfo.getUrl()+" " + paramTypeInfo.getMethod() + " " + paramTypeInfo.getParam() + " " + paramTypeInfo.getApiCollectionId());
                System.out.println(a + " " + b);
                System.out.println(" ");
            }

        }

        resourceAnalyser.syncWithDb();

    }
}



