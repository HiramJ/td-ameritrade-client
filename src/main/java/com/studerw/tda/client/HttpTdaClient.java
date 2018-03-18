package com.studerw.tda.client;

import com.studerw.http.LoggingInterceptor;
import com.studerw.http.cookie.CookieJarImpl;
import com.studerw.http.cookie.store.MemoryCookieStore;
import com.studerw.tda.model.BalancesAndPositions;
import com.studerw.tda.model.Login;
import com.studerw.tda.model.Logout;
import com.studerw.tda.model.OptionChain;
import com.studerw.tda.model.OrderStatus;
import com.studerw.tda.model.QuoteResponse;
import com.studerw.tda.model.QuoteResponseBetter;
import com.studerw.tda.model.SymbolLookupResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpTdaClient implements TdaClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpTdaClient.class);

  protected final OkHttpClient httpClient;
  final String user;
  final byte[] password;
  final private TdaXmlParser tdaXmlParser;
  protected Properties tdProperties;
  Login currentLogin;

  /**
   * @param user TDA user account name
   * @param password TDA user account password
   */
  public HttpTdaClient(String user, byte[] password) {
    LOGGER.info("Initiating HttpTdaClient...");
    this.tdaXmlParser = new TdaXmlParser(this);

    this.user = user;
    this.password = password;
    initTdaProps();
    httpClient = new OkHttpClient.Builder().
        cookieJar(new CookieJarImpl(new MemoryCookieStore())).
        addInterceptor(new TdaLoginInterceptor(this, tdProperties)).
        addInterceptor(new LoggingInterceptor("TDA_HTTP")).
//                connectTimeout(15, TimeUnit.SECONDS).
//                readTimeout(15, TimeUnit.SECONDS).    // socket timeout
    build();

//        this.currentLogin = this.doLogin();
//        LOGGER.info("Logged in with account: {}", currentLogin.getXmlLogIn().getAssociatedAccountId());
  }

  protected HttpUrl baseUrl() {
    return new HttpUrl.Builder()
        .scheme(tdProperties.getProperty("tda.http.schema"))
        .host(tdProperties.getProperty("tda.http.host"))
        .addPathSegment(tdProperties.getProperty("tda.http.path"))
        .build();
  }

  @Override
  public QuoteResponse fetchQuotes(List<String> symbols) {
    LOGGER.debug("Fetching quotes: {}", symbols);
    Builder builder = baseUrl().newBuilder();
    builder.addPathSegments("100/Quote");
    builder.addQueryParameter("source", tdProperties.getProperty("tda.source"));
    builder.addQueryParameter("symbol", StringUtils.join(symbols, " "));
    HttpUrl url = builder.build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseQuoteResponse(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public QuoteResponseBetter fetchQuotesBetter(List<String> symbols) {
    LOGGER.debug("Fetching quotes: {}", symbols);
    Builder builder = baseUrl().newBuilder();
    builder.addPathSegments("100/Quote");
    builder.addQueryParameter("source", tdProperties.getProperty("tda.source"));
//    symbols.stream().forEach(s -> builder.addQueryParameter("symbol", s));
    builder.addQueryParameter("symbol", StringUtils.join(symbols, " "));
    HttpUrl url = builder.build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseQuoteResponseBetter(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }



  @Override
  public Logout logout() {
    LOGGER.debug("Logging out...");
    HttpUrl url = baseUrl().newBuilder().addPathSegments("100/LogOut")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseLogout(response.body().string());

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public BalancesAndPositions fetchBalancesAndPositions(String accountId) {
    LOGGER.debug("Fetching account balance for ID: {}", accountId);
    HttpUrl url = baseUrl().newBuilder().addPathSegments("100/BalancesAndPositions")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .addQueryParameter("accountid", accountId)
//                .addQueryParameter("type", "b")
        .build();

    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseBalances(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean cancelOptionOrder(String orderId) {
    HttpUrl url = baseUrl().newBuilder().addPathSegments("100/OrderCancel")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .addQueryParameter("orderid", orderId)
        .build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      final String xml = response.body().string();
      LOGGER.debug(xml);
      return StringUtils.containsIgnoreCase(xml, "<result>OK</result>");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public OrderStatus fetchOrderStatus(String... orderIds) {
    final HttpUrl.Builder urlBuilder = baseUrl().newBuilder().addPathSegments("100/OrderStatus")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"));
    for (String orderId : orderIds) {
      urlBuilder.addQueryParameter("orderid", orderId);
    }
    final HttpUrl url = urlBuilder.build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseOrderStatus(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  @Override
  public OptionChain fetchOptionChain(String symbol) {
    HttpUrl url = baseUrl().newBuilder().addPathSegments("200/OptionChain")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .addQueryParameter("symbol", symbol)
        .build();

    Request request = new Request.Builder().url(url).build();

    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseOptionChain(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public SymbolLookupResponse symbolLookup(String matchStr) {
    HttpUrl url = baseUrl().newBuilder().addPathSegments("100/SymbolLookup")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .addQueryParameter("matchstring", matchStr)
        .build();

    Request request = new Request.Builder().url(url).build();

    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseSymbolLookupResponse(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public OrderStatus fetchAllOrderStatuses() {
    HttpUrl url = baseUrl().newBuilder().addPathSegments("100/OrderStatus")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return tdaXmlParser.parseOrderStatus(response.body().string());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String keepAlive() {
    HttpUrl url = baseUrl().newBuilder().addPathSegment("KeepAlive")
        .addQueryParameter("source", tdProperties.getProperty("tda.source"))
        .build();
    Request request = new Request.Builder().url(url).build();
    try (Response response = this.httpClient.newCall(request).execute()) {
      return response.body().string();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  //if the currentLogin is null, let's just make a simple call to force a login.
  public Login getCurrentLogin() {
    if (this.currentLogin == null) {
      final String keepAlive = this.keepAlive();
      LOGGER.debug("keep-alive: {}", keepAlive);
    }

    return this.currentLogin;
  }

  private void initTdaProps() {
    try (InputStream in = getClass().getClassLoader()
        .getResourceAsStream("com/studerw/tda/tda-api.properties")) {
      tdProperties = new Properties();
      tdProperties.load(in);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not load default properties from com.studerw.tda.tda-api.properties in classpath");
    }
  }
}
