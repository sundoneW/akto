package com.akto.dependency;

import com.akto.MongoBasedTest;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods;
import com.akto.parsers.HttpCallParser;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDependencyAnalyser extends MongoBasedTest {

    @Test
    public void testAnalyse() throws Exception {
        DependencyNodeDao.instance.getMCollection().drop();

        DependencyAnalyser dependencyAnalyser = new DependencyAnalyser();

        String message1 = "{\"method\":\"POST\",\"requestPayload\":\"{}\",\"responsePayload\":\"{'user': {'name': 'user1', 'age': 0}}\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1000\",\"path\":\"/api/user\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-1.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-1.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-1.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String message2 = "{\"method\":\"POST\",\"requestPayload\":\"{'name': 'user1'}\",\"responsePayload\":\"{ 'company': 'akto.io' }\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1000\",\"path\":\"/api/user_info\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-1.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-1.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-1.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        String message3 = "{\"method\":\"POST\",\"requestPayload\":\"{'company': 'akto.io'}\",\"responsePayload\":\"{ 'reviews': 5, 'CEO': 'Adam Sandler' }\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1000\",\"path\":\"/api/company_info\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-1.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-1.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-1.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";

        List<String> messages = new ArrayList<>();
        messages.add(message1);
        messages.add(message2);
        messages.add(message3);

        for (String message: messages) {
            HttpResponseParams responseParams = HttpCallParser.parseKafkaMessage(message);
            dependencyAnalyser.analyse(responseParams);
        }

        dependencyAnalyser.syncWithDb();

        List<DependencyNode> nodes = DependencyNodeDao.instance.findAll(new BasicDBObject());
        assertEquals(2, nodes.size());

        DependencyNode parentNode = DependencyNodeDao.instance.findOne(DependencyNodeDao.generateParentsFilter(1000, "/api/user_info", URLMethods.Method.POST));
        assertEquals(1, parentNode.getParamInfos().size());
        assertEquals(1, parentNode.getParamInfos().get(0).getCount());

        DependencyNode childNode = DependencyNodeDao.instance.findOne(DependencyNodeDao.generateChildrenFilter(1000, "/api/user_info", URLMethods.Method.POST));
        assertEquals(1, childNode.getParamInfos().size());
        assertEquals(1, childNode.getParamInfos().get(0).getCount());


        String message4 = "{\"method\":\"POST\",\"requestPayload\":\"{'name': 'Adam Sandler'}\",\"responsePayload\":\"{'net_worth': 10000 }\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":\"1000\",\"path\":\"/api/ceo_info\",\"requestHeaders\":\"{\\\"Cookie\\\":\\\"JSESSIONID=node0e7rms6jdk2u41w0drvjv8hkoo0.node0; mp_c403d0b00353cc31d7e33d68dc778806_mixpanel=%7B%22distinct_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24device_id%22%3A%20%22182edbc00381d9-063588c46d5c5e-26021d51-144000-182edbc0039615%22%2C%22%24initial_referrer%22%3A%20%22%24direct%22%2C%22%24initial_referring_domain%22%3A%20%22%24direct%22%7D\\\",\\\"Origin\\\":\\\"dev-1.akto.io\\\",\\\"Accept\\\":\\\"application/json, text/plain, */*\\\",\\\"Access-Control-Allow-Origin\\\":\\\"*\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Referer\\\":\\\"https://dev-1.akto.io/dashboard/settings\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"dev-1.akto.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"access-token\\\":\\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4iLCJzaWduZWRVcCI6InRydWUiLCJ1c2VybmFtZSI6InNoaXZhbnNoQGFrdG8uaW8iLCJpYXQiOjE2NjE4ODAwNTYsImV4cCI6MTY2MTg4MDk1Nn0.wxDbUhIfhX6i8tITykZcdztg8CZUcrBvdqbLgiZJN0Q4QkGOvhHozZ6lwgFzQe3hTOxuFOv8wxg4E_vzruLMgSRmapHGuTi57qTYFWIJNb-VSUa_Nz__t6aXOaXYckO2nvzN2rp1qeTIEKrhLaC_nV5gZpOB2fnBC2Yr1KasERpdDO7I0xc4dqdLQXQRxrWgP6lKlkGKHziCrkvLEWqC7mXrRsS23m-qv4pELm0MikIqf-fl4wmwj7g42769APwAuoQdIgMnUOx2rT1ewkcW72py3wveX96oomdDyvIM6_y5uYALsTymc0xxr1yZOT9Gseypbjm-sa7byVaSbw2s9g\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"sec-ch-ua\\\":\\\"\\\\\\\"Chromium\\\\\\\";v=\\\\\\\"104\\\\\\\", \\\\\\\" Not A;Brand\\\\\\\";v=\\\\\\\"99\\\\\\\", \\\\\\\"Google Chrome\\\\\\\";v=\\\\\\\"104\\\\\\\"\\\",\\\"sec-ch-ua-mobile\\\":\\\"?0\\\",\\\"sec-ch-ua-platform\\\":\\\"\\\\\\\"Windows\\\\\\\"\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.9\\\",\\\"Content-Length\\\":\\\"2\\\",\\\"account\\\":\\\"1000000\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"X-Frame-Options\\\":\\\"deny\\\",\\\"Cache-Control\\\":\\\"no-cache, no-store, must-revalidate, pre-check=0, post-check=0\\\",\\\"Server\\\":\\\"AKTO server\\\",\\\"X-Content-Type-Options\\\":\\\"nosniff\\\",\\\"Content-Encoding\\\":\\\"gzip\\\",\\\"Vary\\\":\\\"Accept-Encoding, User-Agent\\\",\\\"Content-Length\\\":\\\"150\\\",\\\"X-XSS-Protection\\\":\\\"1\\\",\\\"Content-Language\\\":\\\"en-US\\\",\\\"Date\\\":\\\"Tue, 30 Aug 2022 17:22:40 GMT\\\",\\\"Content-Type\\\":\\\"application/json;charset=utf-8\\\"}\",\"time\":\"1661880160\",\"contentType\":\"application/json;charset=utf-8\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}";
        messages.add(message4);

        for (String message: messages) {
            HttpResponseParams responseParams = HttpCallParser.parseKafkaMessage(message);
            dependencyAnalyser.analyse(responseParams);
        }

        dependencyAnalyser.syncWithDb();

        nodes = DependencyNodeDao.instance.findAll(new BasicDBObject());
        assertEquals(3, nodes.size());

        parentNode = DependencyNodeDao.instance.findOne(DependencyNodeDao.generateParentsFilter(1000, "/api/user_info", URLMethods.Method.POST));
        assertEquals(1, parentNode.getParamInfos().size());
        assertEquals(2, parentNode.getParamInfos().get(0).getCount());

        childNode = DependencyNodeDao.instance.findOne(DependencyNodeDao.generateChildrenFilter(1000, "/api/user_info", URLMethods.Method.POST));
        assertEquals(1, childNode.getParamInfos().size());
        assertEquals(2, childNode.getParamInfos().get(0).getCount());

        parentNode = DependencyNodeDao.instance.findOne(DependencyNodeDao.generateChildrenFilter(1000, "/api/company_info", URLMethods.Method.POST));
        assertEquals(1, parentNode.getParamInfos().size());
        assertEquals(1, parentNode.getParamInfos().get(0).getCount());

    }
}
