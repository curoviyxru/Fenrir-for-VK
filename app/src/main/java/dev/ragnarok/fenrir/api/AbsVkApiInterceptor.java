package dev.ragnarok.fenrir.api;

import android.os.SystemClock;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Random;

import dev.ragnarok.fenrir.Account_Types;
import dev.ragnarok.fenrir.Constants;
import dev.ragnarok.fenrir.Injection;
import dev.ragnarok.fenrir.api.model.Captcha;
import dev.ragnarok.fenrir.api.model.Error;
import dev.ragnarok.fenrir.api.model.response.VkReponse;
import dev.ragnarok.fenrir.exception.UnauthorizedException;
import dev.ragnarok.fenrir.service.ApiErrorCodes;
import dev.ragnarok.fenrir.settings.Settings;
import dev.ragnarok.fenrir.util.PersistentLogger;
import dev.ragnarok.fenrir.util.Utils;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static dev.ragnarok.fenrir.util.Objects.isNull;
import static dev.ragnarok.fenrir.util.Objects.nonNull;
import static dev.ragnarok.fenrir.util.Utils.isEmpty;


abstract class AbsVkApiInterceptor implements Interceptor {

    private static final Random RANDOM = new Random();
    private final String version;
    private final Gson gson;

    AbsVkApiInterceptor(String version, Gson gson) {
        this.version = version;
        this.gson = gson;
    }

    protected abstract String getToken();

    protected abstract @Account_Types
    int getType();

    protected abstract int getAccountId();

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        String token = getToken();

        if (isEmpty(token)) {
            throw new UnauthorizedException("No authorization! Please, login and retry");
        }

        FormBody.Builder formBuiler = new FormBody.Builder();

        RequestBody body = original.body();

        boolean HasVersion = false;
        boolean HasDeviceId = false;
        if (body instanceof FormBody) {
            FormBody formBody = (FormBody) body;
            for (int i = 0; i < formBody.size(); i++) {
                String name = formBody.name(i);
                if (name.equals("v"))
                    HasVersion = true;
                else if (name.equals("device_id"))
                    HasDeviceId = true;
                String value = formBody.value(i);
                formBuiler.add(name, value);
            }
        }
        if (!HasVersion)
            formBuiler.add("v", version);

        formBuiler.add("access_token", token)
                .add("lang", Constants.DEVICE_COUNTRY_CODE)
                .add("https", "1");
        if (!HasDeviceId)
            formBuiler.add("device_id", Utils.getDiviceId(Injection.provideApplicationContext()));

        Request request = original.newBuilder()
                .method("POST", formBuiler.build())
                .build();

        Response response;
        ResponseBody responseBody;
        String responseBodyString;

        while (true) {
            response = chain.proceed(request);
            responseBody = response.body();
            assert responseBody != null;
            responseBodyString = responseBody.string();

            VkReponse vkReponse;
            try {
                vkReponse = gson.fromJson(responseBodyString, VkReponse.class);
            } catch (JsonSyntaxException ignored) {
                responseBodyString = "{ \"error\": { \"error_code\": -1, \"error_msg\": \"Internal json syntax error\" } }";
                return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBody.contentType())).build();
            }

            Error error = isNull(vkReponse) ? null : vkReponse.error;

            if (nonNull(error)) {
                switch (error.errorCode) {
                    case ApiErrorCodes.TOO_MANY_REQUESTS_PER_SECOND:
                        break;
                    case ApiErrorCodes.CAPTCHA_NEED:
                        if (Settings.get().other().isDeveloper_mode()) {
                            PersistentLogger.logThrowable("Captcha request", new Exception("URL: " + request.url() + ", dump: " + new Gson().toJson(error)));
                        }
                        break;
                    default:
                        //FirebaseCrash.log("ApiError, method: " + error.method + ", code: " + error.errorCode + ", message: " + error.errorMsg);
                        break;
                }

                if (error.errorCode == ApiErrorCodes.TOO_MANY_REQUESTS_PER_SECOND) {
                    synchronized (AbsVkApiInterceptor.class) {
                        int sleepMs = 1000 + RANDOM.nextInt(500);
                        SystemClock.sleep(sleepMs);
                    }

                    continue;
                }

                if (error.errorCode == ApiErrorCodes.CAPTCHA_NEED) {
                    Captcha captcha = new Captcha(error.captchaSid, error.captchaImg);

                    ICaptchaProvider provider = Injection.provideCaptchaProvider();
                    provider.requestCaptha(captcha.getSid(), captcha);

                    String code = null;

                    while (true) {
                        try {
                            code = provider.lookupCode(captcha.getSid());

                            if (nonNull(code)) {
                                break;
                            } else {
                                SystemClock.sleep(1000);
                            }
                        } catch (OutOfDateException e) {
                            break;
                        }
                    }
                    if (Settings.get().other().isDeveloper_mode()) {
                        PersistentLogger.logThrowable("Captcha answer", new Exception("URL: " + request.url() + ", code: " + code + ", sid: " + captcha.getSid()));
                    }
                    if (nonNull(code)) {
                        formBuiler.add("captcha_sid", captcha.getSid());
                        formBuiler.add("captcha_key", code);

                        request = original.newBuilder()
                                .method("POST", formBuiler.build())
                                .build();
                        continue;
                    }
                }
            }
            break;
        }
        return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBody.contentType())).build();
    }
}