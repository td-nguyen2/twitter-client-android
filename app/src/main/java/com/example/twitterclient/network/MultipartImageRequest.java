
package com.example.twitterclient.network;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MultipartImageRequest extends Request<JSONObject> {

    private final String boundary =
            "Boundary-" + UUID.randomUUID();

    private final byte[] imageBytes;
    private final String token;
    private final Response.Listener<JSONObject> listener;

    public MultipartImageRequest(
            String url,
            byte[] imageBytes,
            String token,
            Response.Listener<JSONObject> listener,
            Response.ErrorListener errorListener
    ) {
        super(Method.POST, url, errorListener);

        this.imageBytes = imageBytes;
        this.token = token;
        this.listener = listener;

        setShouldCache(false);
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            String header =
                    "--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; "
                            + "name=\"image\"; filename=\"photo.jpg\"\r\n"
                            + "Content-Type: application/octet-stream\r\n\r\n";

            output.write(
                    header.getBytes(StandardCharsets.UTF_8)
            );

            output.write(imageBytes);

            String footer =
                    "\r\n--" + boundary + "--\r\n";

            output.write(
                    footer.getBytes(StandardCharsets.UTF_8)
            );

            return output.toByteArray();

        } catch (Exception error) {
            throw new IllegalStateException(
                    "Could not create multipart request",
                    error
            );
        }
    }

    @Override
    protected Response<JSONObject> parseNetworkResponse(
            NetworkResponse response
    ) {
        try {
            String json = new String(
                    response.data,
                    StandardCharsets.UTF_8
            );

            return Response.success(
                    new JSONObject(json),
                    HttpHeaderParser.parseCacheHeaders(response)
            );

        } catch (Exception error) {
            return Response.error(new VolleyError(error));
        }
    }

    @Override
    protected void deliverResponse(JSONObject response) {
        listener.onResponse(response);
    }
}
