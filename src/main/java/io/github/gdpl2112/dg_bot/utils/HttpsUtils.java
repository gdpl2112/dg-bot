package io.github.gdpl2112.dg_bot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class HttpsUtils {
    private static final Logger log = LoggerFactory.getLogger(HttpsUtils.class);

    //调用该类直接跳过ssl证书
    static {
        try {
            trustAllHttpsCertificates();
            HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);
        } catch (Exception e) {
        }
    }

    /**
     * 向指定 URL 发送POST方法的请求
     *
     * @param url   发送请求的 URL
     * @param param 请求参数
     * @return 所代表远程资源的响应结果
     */
    public static Map<String, Object> sendPostWithStatus(String url, String param) {
        Map<String, Object> map = new HashMap<>();
        PrintWriter out = null;
        String status = null;
        BufferedReader in = null;
        StringBuilder result = new StringBuilder();
        try {
            String urlNameString = url;
            log.info("sendPost - {}", urlNameString);
            URL realUrl = new URL(urlNameString);

            // 打开连接
            HttpURLConnection conn = (HttpURLConnection) realUrl.openConnection();
            // 默认是 GET方式
            conn.setRequestMethod("POST");
            // 设置是否向connection输出，因为这个是post请求，参数要放在http正文内，因此需要设为true
            conn.setDoOutput(true);
            conn.setDoInput(true);
            // Post 请求不能使用缓存
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(true);
            // 配置本次连接的Content-type，参数类型为json
            conn.setRequestProperty("Content-Type", "application/json");
            // 通用请求头信息
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            conn.setRequestProperty("Accept-Charset", "utf-8");
            conn.setRequestProperty("contentType", "utf-8");

            out = new PrintWriter(conn.getOutputStream());
            //请求参数
            out.print(param);
            out.flush();
            //获取响应的状态码 请求成功为200
            status = new Integer(((HttpURLConnection) conn).getResponseCode()).toString();
            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String line;
            //获取响应内容
            while ((line = in.readLine()) != null) {
                result.append(line);
            }
            log.info("recv - {}", result);
        } catch (ConnectException e) {
            log.error("调用HttpUtils.sendPostWithStatus ConnectException, url=" + url + ",param=" + param, e);
        } catch (SocketTimeoutException e) {
            log.error("调用HttpUtils.sendPostWithStatus SocketTimeoutException, url=" + url + ",param=" + param, e);
        } catch (IOException e) {
            log.error("调用HttpUtils.sendPostWithStatus IOException, url=" + url + ",param=" + param, e);
        } catch (Exception e) {
            log.error("调用HttpsUtil.sendPostWithStatus Exception, url=" + url + ",param=" + param, e);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                log.error("调用in.close Exception, url=" + url + ",param=" + param, ex);
            }
        }
        map.put("status", status);
        map.put("result", result);
        return map;
    }

    /**
     * 跳过ssl证书
     *
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    public static void trustAllHttpsCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[1];
        trustAllCerts[0] = (TrustManager) new TrustAllManager();
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    private static class TrustAllManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }
    }
}

