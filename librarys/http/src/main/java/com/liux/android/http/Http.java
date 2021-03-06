package com.liux.android.http;

import android.content.Context;

import androidx.annotation.IntDef;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liux.android.http.cookie.DefaultCookieJar;
import com.liux.android.http.dns.TimeOutDns;
import com.liux.android.http.interceptor.BaseUrlInterceptor;
import com.liux.android.http.interceptor.HttpLoggingInterceptor;
import com.liux.android.http.interceptor.RequestInterceptor;
import com.liux.android.http.interceptor.TimeoutInterceptor;
import com.liux.android.http.interceptor.UserAgentInterceptor;
import com.liux.android.http.request.BodyRequest;
import com.liux.android.http.request.QueryRequest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * 基于 OkHttp3 封装的Http客户端 <br>
 * 0.全局单例模式 <br>
 * 1.GET/HEAD/POST/DELETE/PUT/PATCH六种方法的同步/异步访问调用 <br>
 * 2.Retorfit2 + RxJava3 支持 <br>
 * 3.数据解析使用 Jackson <br>
 * 4.请求头/请求参数回调 <br>
 * 5.超时时间/BaseUrl/UserAgent灵活设置 <br>
 * 6.请求数据进度回调支持 <br>
 * 7.流传输能力 <br>
 * @author Liux
 */

public class Http {
    private static volatile Http mInstance;
    public static Http get() {
        if (mInstance == null) throw new NullPointerException("Http has not been initialized");
        return mInstance;
    }
    public static boolean isInit() {
        synchronized(Http.class) {
            return mInstance != null;
        }
    }
    public static void init(Context context, String baseUrl) {
        init(
                context,
                new OkHttpClient.Builder()
                        .dns(new TimeOutDns(5, TimeUnit.SECONDS, 2))
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .pingInterval(30, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .cookieJar(new DefaultCookieJar()),
                new Retrofit.Builder()
                        .baseUrl(baseUrl),
                JsonUtil.getDefaultObjectMapper()
        );
    }
    public static void init(Context context, OkHttpClient.Builder okHttpBuilder, Retrofit.Builder retrofitBuilder, ObjectMapper objectMapper) {
        if (mInstance != null) return;
        synchronized(Http.class) {
            if (mInstance != null) return;
            mInstance = new Http(context, okHttpBuilder, retrofitBuilder, objectMapper);
        }
    }
    public static void release() {
        if (mInstance != null) {
            mInstance.mRequestInterceptor.setHeaderCallback(null);
            mInstance.mRequestInterceptor.setRequestCallback(null);
            mInstance.mOkHttpClient.dispatcher().executorService().shutdown();
            mInstance.mOkHttpClient.connectionPool().evictAll();
            mInstance = null;
        }
    }

    public static final String TAG = "[Http]";

    private Context mContext;
    private Retrofit mRetrofit;
    private OkHttpClient mOkHttpClient;

    private TimeoutInterceptor mTimeoutInterceptor;
    private BaseUrlInterceptor mBaseUrlInterceptor;
    private UserAgentInterceptor mUserAgentInterceptor;
    private RequestInterceptor mRequestInterceptor;
    private HttpLoggingInterceptor mHttpLoggingInterceptor;

    private Http(Context context, OkHttpClient.Builder okHttpBuilder, Retrofit.Builder retrofitBuilder, ObjectMapper objectMapper) {
        if (context == null) throw new NullPointerException("Context required");
        if (okHttpBuilder == null) throw new NullPointerException("OkHttpClient.Builder required");
        if (retrofitBuilder == null) throw new NullPointerException("Retorfit.Builder required");

        mContext = context;

        mTimeoutInterceptor = new TimeoutInterceptor();
        mBaseUrlInterceptor = new BaseUrlInterceptor(this);
        mUserAgentInterceptor = new UserAgentInterceptor(mContext);
        mRequestInterceptor = new RequestInterceptor();
        mHttpLoggingInterceptor = new HttpLoggingInterceptor();

        okHttpBuilder
                .addInterceptor(mTimeoutInterceptor)
                .addInterceptor(mBaseUrlInterceptor)
                .addInterceptor(mUserAgentInterceptor)
                .addInterceptor(mRequestInterceptor)
                .addInterceptor(mHttpLoggingInterceptor);
        mOkHttpClient = okHttpBuilder.build();

        retrofitBuilder
                .client(mOkHttpClient)
                .validateEagerly(true)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper));
        CallAdapter.Factory factory;
        factory = HttpUtil.getRxJava3CallAdapterFactory();
        if (factory != null) retrofitBuilder.addCallAdapterFactory(factory);
        mRetrofit = retrofitBuilder.build();

        mTimeoutInterceptor.setOverallConnectTimeout(mOkHttpClient.connectTimeoutMillis(), TimeUnit.MILLISECONDS);
        mTimeoutInterceptor.setOverallWriteTimeout(mOkHttpClient.writeTimeoutMillis(), TimeUnit.MILLISECONDS);
        mTimeoutInterceptor.setOverallReadTimeout(mOkHttpClient.readTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public QueryRequest get(String url) {
        return new QueryRequest(getOkHttpClient(), QueryRequest.Method.GET).url(url);
    }

    public QueryRequest head(String url) {
        return new QueryRequest(getOkHttpClient(), QueryRequest.Method.HEAD).url(url);
    }

    public BodyRequest post(String url) {
        return new BodyRequest(mContext, getOkHttpClient(), BodyRequest.Method.POST).url(url);
    }

    public BodyRequest delete(String url) {
        return new BodyRequest(mContext, getOkHttpClient(), BodyRequest.Method.DELETE).url(url);
    }

    public BodyRequest put(String url) {
        return new BodyRequest(mContext, getOkHttpClient(), BodyRequest.Method.PUT).url(url);
    }

    public BodyRequest patch(String url) {
        return new BodyRequest(mContext, getOkHttpClient(), BodyRequest.Method.PATCH).url(url);
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * 取 OkHttpClient 实例
     * @return
     */
    public OkHttpClient getOkHttpClient() {
        return mOkHttpClient;
    }

    /**
     * 取 Retrofit 实例
     * @return
     */
    public Retrofit getRetrofit() {
        return mRetrofit;
    }

    /**
     * 取 Retrofit 服务
     * @param service
     * @param <T>
     * @return
     */
    public <T> T getService(Class<T> service) {
        return getRetrofit().create(service);
    }

    /**
     * 取当前用户识别标志
     * @return
     */
    public String getUserAgent() {
        return mUserAgentInterceptor.getUserAgent();
    }

    /**
     * 设置当前用户识别标志
     * @param userAgent
     * @return
     */
    public Http setUserAgent(String userAgent) {
        mUserAgentInterceptor.setUserAgent(userAgent);
        return this;
    }

    public static final int LOG_LEVEL_NONE = 0;
    public static final int LOG_LEVEL_BASIC = 1;
    public static final int LOG_LEVEL_HEADERS = 2;
    public static final int LOG_LEVEL_BODY = 3;
    /**
     * 设置打印日志级别
     * @param logLevel
     * @return
     */
    public Http setLoggingLevel(@LogLevel int logLevel) {
        HttpLoggingInterceptor.Level level;
        switch (logLevel) {
            case LOG_LEVEL_BODY:
                level = HttpLoggingInterceptor.Level.BODY;
                break;
            case LOG_LEVEL_HEADERS:
                level = HttpLoggingInterceptor.Level.HEADERS;
                break;
            case LOG_LEVEL_BASIC:
                level = HttpLoggingInterceptor.Level.BASIC;
                break;
            case LOG_LEVEL_NONE:
            default:
                level = HttpLoggingInterceptor.Level.NONE;
                break;
        }
        mHttpLoggingInterceptor.setLevel(level);
        return this;
    }

    /**
     * 设置监听
     * @param callback
     * @return
     */
    public Http setCallback(Callback callback) {
        setHeaderCallback(callback);
        setRequestCallback(callback);
        return this;
    }

    /**
     * 设置请求头监听
     * @param callback
     * @return
     */
    public Http setHeaderCallback(HeaderCallback callback) {
        mRequestInterceptor.setHeaderCallback(callback);
        return this;
    }

    /**
     * 设置请求监听
     * @param callback
     * @return
     */
    public Http setRequestCallback(RequestCallback callback) {
        mRequestInterceptor.setRequestCallback(callback);
        return this;
    }

    public static final String HEADER_BASE_URL = BaseUrlInterceptor.HEADER_BASE_URL;
    public static final String HEADER_BASE_RULE = BaseUrlInterceptor.HEADER_BASE_RULE;

    /**
     * 获取当前全局BaseUrl
     * @return
     */
    public String getBaseUrl() {
        return mBaseUrlInterceptor.getBaseUrl();
    }

    /**
     * 设置当前全局BaseUrl
     *
     * @Headers({
     *         Http.HEADER_BASE_URL + "https://api.domain.com:88/api/"
     * })
     *
     * @param baseUrl
     * @return
     */
    public Http setBaseUrl(String baseUrl) {
        checkBaseUrl(baseUrl);
        mBaseUrlInterceptor.setBaseUrl(baseUrl);
        return this;
    }

    /**
     * 获取某个规则对应的URL
     * @param rule
     * @return
     */
    public String getDomainRule(String rule) {
        String url = mBaseUrlInterceptor.getDomainRule(rule);
        return url;
    }

    /**
     * 加入某个URL对应的规则
     *
     * @Headers({
     *         Http.HEADER_BASE_RULE + "{rule}"
     * })
     *
     * @param rule
     * @param baseUrl
     * @return
     */
    public Http putDomainRule(String rule, String baseUrl) {
        checkBaseUrl(baseUrl);
        mBaseUrlInterceptor.putDomainRule(rule, baseUrl);
        return this;
    }

    /**
     * 获取所有URL对应规则,Copy目的是防止跳过检查添加规则
     * @return
     */
    public Map<String, String> getDomainRules() {
        return mBaseUrlInterceptor.getDomainRules();
    }

    /**
     * 清除所有URL对应规则
     * @return
     */
    public Http clearDomainRules() {
        mBaseUrlInterceptor.clearDomainRules();
        return this;
    }

    public static final String HEADER_TIMEOUT_CONNECT = TimeoutInterceptor.HEADER_TIMEOUT_CONNECT;
    public static final String HEADER_TIMEOUT_WRITE = TimeoutInterceptor.HEADER_TIMEOUT_WRITE;
    public static final String HEADER_TIMEOUT_READ = TimeoutInterceptor.HEADER_TIMEOUT_READ;

    /**
     * 获取全局连接超时时间
     * @return
     */
    public int getOverallConnectTimeoutMillis() {
        return mTimeoutInterceptor.getOverallConnectTimeoutMillis();
    }

    /**
     * 设置全局连接超时时间
     * @param overallConnectTimeout
     * @param timeUnit
     * @return
     */
    public Http setOverallConnectTimeout(int overallConnectTimeout, TimeUnit timeUnit) {
        mTimeoutInterceptor.setOverallConnectTimeout(overallConnectTimeout, timeUnit);
        return this;
    }

    /**
     * 获取全局写超时时间
     * @return
     */
    public int getOverallWriteTimeoutMillis() {
        return mTimeoutInterceptor.getOverallWriteTimeoutMillis();
    }

    /**
     * 设置全局写超时时间
     * @param overallWriteTimeout
     * @param timeUnit
     * @return
     */
    public Http setOverallWriteTimeout(int overallWriteTimeout, TimeUnit timeUnit) {
        mTimeoutInterceptor.setOverallConnectTimeout(overallWriteTimeout, timeUnit);
        return this;
    }

    /**
     * 获取全局读超时时间
     * @return
     */
    public int getOverallReadTimeoutMillis() {
        return mTimeoutInterceptor.getOverallReadTimeoutMillis();
    }

    /**
     * 设置全局读超时时间
     * @param overallReadTimeout
     * @param timeUnit
     * @return
     */
    public Http setOverallReadTimeout(int overallReadTimeout, TimeUnit timeUnit) {
        mTimeoutInterceptor.setOverallConnectTimeout(overallReadTimeout, timeUnit);
        return this;
    }

    /**
     * 预检查BaseUrl格式
     * @param baseUrl
     */
    private void checkBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new NullPointerException("baseUrl == null");
        }
        HttpUrl httpUrl = HttpUrl.parse(baseUrl);
        if (httpUrl == null) {
            throw new IllegalArgumentException("Illegal URL: " + baseUrl);
        }
        List<String> pathSegments = httpUrl.pathSegments();
        if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
            throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
        }
    }

    @IntDef({LOG_LEVEL_NONE, LOG_LEVEL_BASIC, LOG_LEVEL_HEADERS, LOG_LEVEL_BODY})
    @Retention(RetentionPolicy.SOURCE)
    @interface LogLevel{}
}